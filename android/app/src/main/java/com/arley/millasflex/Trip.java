package com.arley.millasflex;

import org.json.JSONException;
import org.json.JSONObject;

// Un viaje registrado: fecha, millas, tipo (Trabajo o Personal).
public class Trip {
    public long id;            // identificador único (fecha en milisegundos)
    public long startTime;     // inicio (milisegundos)
    public long endTime;       // fin (milisegundos)
    public double miles;       // millas recorridas
    public String type;        // "Trabajo" o "Personal"
    public boolean manual;     // true si se apuntó a mano

    public Trip(long id, long startTime, long endTime, double miles, String type, boolean manual) {
        this.id = id;
        this.startTime = startTime;
        this.endTime = endTime;
        this.miles = miles;
        this.type = type;
        this.manual = manual;
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("startTime", startTime);
        o.put("endTime", endTime);
        o.put("miles", miles);
        o.put("type", type);
        o.put("manual", manual);
        return o;
    }

    public static Trip fromJson(JSONObject o) throws JSONException {
        return new Trip(
                o.getLong("id"),
                o.getLong("startTime"),
                o.getLong("endTime"),
                o.getDouble("miles"),
                o.optString("type", "Trabajo"),
                o.optBoolean("manual", false)
        );
    }
}
