package com.arley.millasflex;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Pantalla principal, todo en español.
// Botones: Empezar viaje / Terminar viaje / Agregar manual / Exportar.
// La lista muestra los viajes guardados; mantener presionado uno lo borra.
public class MainActivity extends Activity {

    private static final int REQ_LOCATION = 1;
    private static final int REQ_BACKGROUND_LOCATION = 2;
    private static final int REQ_NOTIFICATIONS = 3;

    // Tarifa del IRS 2025 para estimar la deducción. La de 2026 aún no se
    // anuncia; cuando salga, cambia este número y reconstruye el APK.
    private static final double IRS_RATE_2025 = 0.70;

    private TextView txtEstado;
    private TextView txtMillas;
    private TextView txtTiempo;
    private TextView txtTotales;
    private Spinner spnTipo;
    private Button btnEmpezar;
    private Button btnTerminar;
    private ListView listViajes;

    private boolean tracking = false;
    private List<Trip> trips = new ArrayList<>();
    private List<String> tripLines = new ArrayList<>();
    private ArrayAdapter<String> adapter;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            if (TrackingService.BROADCAST_UPDATE.equals(intent.getAction())) {
                double miles = intent.getDoubleExtra(TrackingService.EXTRA_MILES, 0);
                long min = intent.getLongExtra(TrackingService.EXTRA_ELAPSED_MIN, 0);
                txtMillas.setText(String.format(Locale.US, "%.2f mi", miles));
                txtTiempo.setText(min + " min");
            } else if (TrackingService.BROADCAST_SAVED.equals(intent.getAction())) {
                tracking = false;
                updateUi();
                refreshList();
                Toast.makeText(MainActivity.this,
                        "Viaje guardado", Toast.LENGTH_SHORT).show();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtEstado = findViewById(R.id.txtEstado);
        txtMillas = findViewById(R.id.txtMillas);
        txtTiempo = findViewById(R.id.txtTiempo);
        txtTotales = findViewById(R.id.txtTotales);
        spnTipo = findViewById(R.id.spnTipo);
        btnEmpezar = findViewById(R.id.btnEmpezar);
        btnTerminar = findViewById(R.id.btnTerminar);
        Button btnManual = findViewById(R.id.btnManual);
        Button btnExportar = findViewById(R.id.btnExportar);
        listViajes = findViewById(R.id.listViajes);

        ArrayAdapter<String> tipoAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item,
                new String[]{"Trabajo", "Personal"});
        tipoAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnTipo.setAdapter(tipoAdapter);

        adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, tripLines);
        listViajes.setAdapter(adapter);

        // Mantener presionado un viaje lo borra (con confirmación).
        listViajes.setOnItemLongClickListener((parent, view, position, id) -> {
            if (position < 0 || position >= trips.size()) return true;
            Trip t = trips.get(position);
            new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Borrar viaje")
                    .setMessage("¿Borrar este viaje de "
                            + String.format(Locale.US, "%.2f", t.miles) + " millas?")
                    .setPositiveButton("Borrar", (d, w) -> {
                        TripStore.delete(MainActivity.this, t.id);
                        refreshList();
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return true;
        });

        btnEmpezar.setOnClickListener(v -> onEmpezar());
        btnTerminar.setOnClickListener(v -> onTerminar());
        btnManual.setOnClickListener(v -> onAgregarManual());
        btnExportar.setOnClickListener(v -> onExportar());

        // Si había un viaje en curso cuando se cerró la app, muestra el estado.
        SharedPreferences prefs = getSharedPreferences("tracking", MODE_PRIVATE);
        tracking = prefs.getBoolean("active", false);
        if (tracking) {
            txtMillas.setText(String.format(Locale.US, "%.2f mi",
                    prefs.getFloat("miles", 0f)));
        }
        updateUi();
        refreshList();
        askNotificationPermission();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TrackingService.BROADCAST_UPDATE);
        filter.addAction(TrackingService.BROADCAST_SAVED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(receiver, filter);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(receiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    private void onEmpezar() {
        // 1) Permiso de ubicación precisa.
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    REQ_LOCATION);
            return;
        }
        // 2) Permiso de ubicación TODO EL TIEMPO (segundo plano).
        // Android lo pide por separado y solo después del permiso preciso.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQ_BACKGROUND_LOCATION);
            Toast.makeText(this,
                    "Elige 'Permitir todo el tiempo' para que cuente con la pantalla apagada",
                    Toast.LENGTH_LONG).show();
            return;
        }
        startServiceNow();
    }

    private void startServiceNow() {
        String type = (String) spnTipo.getSelectedItem();
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(TrackingService.ACTION_START);
        i.putExtra("type", type);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        tracking = true;
        // Mantiene la pantalla encendida mientras registra (gasta batería,
        // pero evita que el teléfono se duerma en medio del viaje).
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        txtMillas.setText("0.00 mi");
        txtTiempo.setText("0 min");
        updateUi();
        Toast.makeText(this, "Registrando millas...", Toast.LENGTH_SHORT).show();
    }

    private void onTerminar() {
        Intent i = new Intent(this, TrackingService.class);
        i.setAction(TrackingService.ACTION_STOP);
        startService(i);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void onAgregarManual() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);

        final EditText inputMillas = new EditText(this);
        inputMillas.setHint("Millas (ejemplo: 12.5)");
        inputMillas.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        layout.addView(inputMillas);

        new AlertDialog.Builder(this)
                .setTitle("Agregar viaje a mano")
                .setView(layout)
                .setPositiveButton("Guardar", (d, w) -> {
                    try {
                        double miles = Double.parseDouble(
                                inputMillas.getText().toString().replace(",", "."));
                        if (miles <= 0) {
                            Toast.makeText(this, "Pon un número mayor que cero",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        String type = (String) spnTipo.getSelectedItem();
                        long now = System.currentTimeMillis();
                        TripStore.add(this, new Trip(now, now, now, miles, type, true));
                        refreshList();
                        Toast.makeText(this, "Viaje guardado",
                                Toast.LENGTH_SHORT).show();
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "Ese número no es válido",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void onExportar() {
        List<Trip> all = TripStore.load(this);
        if (all.isEmpty()) {
            Toast.makeText(this, "Aún no hay viajes para exportar",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String csv = TripStore.toCsv(all);
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(ClipData.newPlainText("viajes-millas", csv));
        Toast.makeText(this,
                "CSV copiado: pégalo en una nota o un correo",
                Toast.LENGTH_LONG).show();
    }

    private void updateUi() {
        if (tracking) {
            txtEstado.setText("Registrando millas...");
            btnEmpezar.setEnabled(false);
            btnTerminar.setEnabled(true);
        } else {
            txtEstado.setText("Sin registrar");
            btnEmpezar.setEnabled(true);
            btnTerminar.setEnabled(false);
        }
    }

    private void refreshList() {
        trips = TripStore.load(this);
        tripLines.clear();
        double total = 0;
        double trabajo = 0;
        double personal = 0;
        SimpleDateFormat fmt = new SimpleDateFormat("dd MMM yyyy HH:mm", Locale.US);
        for (Trip t : trips) {
            total += t.miles;
            if ("Trabajo".equals(t.type)) {
                trabajo += t.miles;
            } else {
                personal += t.miles;
            }
            tripLines.add(fmt.format(new Date(t.startTime)) + "  -  "
                    + String.format(Locale.US, "%.2f", t.miles) + " mi  -  "
                    + t.type + (t.manual ? " (a mano)" : ""));
        }
        if (trips.isEmpty()) {
            tripLines.add("Aún no hay viajes. Toca 'Empezar viaje' cuando salgas.");
        }
        adapter.notifyDataSetChanged();
        txtTotales.setText("Total: " + String.format(Locale.US, "%.2f", total)
                + " mi   |   Trabajo: " + String.format(Locale.US, "%.2f", trabajo)
                + " mi   |   Personal: " + String.format(Locale.US, "%.2f", personal)
                + " mi\nDeducción estimada (trabajo, IRS 2025 $0.70/mi): $"
                + String.format(Locale.US, "%.2f", trabajo * IRS_RATE_2025));
    }

    private void askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    REQ_NOTIFICATIONS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions,
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length == 0) return;
        if (requestCode == REQ_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Con el permiso preciso, ahora pide el de segundo plano.
                onEmpezar();
            } else {
                Toast.makeText(this,
                        "Sin permiso de ubicación la app no puede contar millas",
                        Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == REQ_BACKGROUND_LOCATION) {
            // Aunque diga que no al permiso de segundo plano, la app igual
            // registra mientras esté abierta; solo no contará con la pantalla
            // apagada. Se empieza el viaje de todos modos.
            startServiceNow();
            if (grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Sin 'todo el tiempo', solo contará con la app abierta",
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
