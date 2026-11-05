package com.arley.millasflex;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

// Guarda los viajes en un archivo JSON dentro del teléfono.
// Nada sale del teléfono: no hay cuentas, no hay nube, no hay internet.
public class TripStore {
    private static final String FILE_NAME = "viajes.json";

    public static List<Trip> load(Context ctx) {
        List<Trip> trips = new ArrayList<>();
        try {
            FileInputStream in = ctx.openFileInput(FILE_NAME);
            byte[] bytes = new byte[in.available()];
            //noinspection ResultOfMethodCallIgnored
            in.read(bytes);
            in.close();
            String text = new String(bytes, StandardCharsets.UTF_8);
            JSONArray arr = new JSONArray(text);
            for (int i = 0; i < arr.length(); i++) {
                trips.add(Trip.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            // Si no hay archivo todavía, la lista empieza vacía. No es un error.
        }
        return trips;
    }

    public static void save(Context ctx, List<Trip> trips) {
        try {
            JSONArray arr = new JSONArray();
            for (Trip t : trips) {
                arr.put(t.toJson());
            }
            FileOutputStream out = ctx.openFileOutput(FILE_NAME, Context.MODE_PRIVATE);
            out.write(arr.toString().getBytes(StandardCharsets.UTF_8));
            out.close();
        } catch (Exception e) {
            // Si no se pudo guardar, no se rompe la app; el viaje en curso se pierde.
        }
    }

    public static void add(Context ctx, Trip trip) {
        List<Trip> trips = load(ctx);
        trips.add(0, trip); // el más reciente arriba
        save(ctx, trips);
    }

    public static void delete(Context ctx, long id) {
        List<Trip> trips = load(ctx);
        for (int i = 0; i < trips.size(); i++) {
            if (trips.get(i).id == id) {
                trips.remove(i);
                break;
            }
        }
        save(ctx, trips);
    }

    // Texto CSV con todos los viajes, para copiarlo o pegarlo donde quieras.
    public static String toCsv(List<Trip> trips) {
        StringBuilder sb = new StringBuilder();
        sb.append("fecha,millas,tipo,manual\n");
        java.text.SimpleDateFormat fmt =
                new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US);
        for (Trip t : trips) {
            sb.append(fmt.format(new java.util.Date(t.startTime))).append(",");
            sb.append(String.format(java.util.Locale.US, "%.2f", t.miles)).append(",");
            sb.append(t.type).append(",");
            sb.append(t.manual ? "si" : "no").append("\n");
        }
        return sb.toString();
    }
}
