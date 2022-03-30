package planespotter.dataclasses;

import planespotter.dataclasses.DataPoint;

import javax.swing.*;

public class ListObject {

    private final DataPoint data;
    private final String title;

    public ListObject (DataPoint p) {
        data = p;
        title = "Flightnr.: " + data.getFlight().getFlightnr() + ", Airline: " + data.getFlight().getPlane().getAirline().getName();
    }

    /**
     * getter
     */
    public String getTitle () { return title; }

}
