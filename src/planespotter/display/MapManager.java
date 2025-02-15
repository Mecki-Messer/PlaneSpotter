package planespotter.display;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.openstreetmap.gui.jmapviewer.*;
import org.openstreetmap.gui.jmapviewer.interfaces.*;
import planespotter.controller.ActionHandler;
import planespotter.dataclasses.*;
import planespotter.display.models.SimulationAddons;
import planespotter.util.Utilities;
import planespotter.util.math.MathUtils;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * @name MapManager
 * @author jml04
 * @version 1.1
 *
 * @description
 *  manages the map data and contains methods which are executed on the mapView
 */
public final class MapManager {

    // reference to the UserInterface
    private final UserInterface ui;

    // map viewer
    private final TreasureMap mapViewer;

    // current selected (clicked) ICAO
    @Nullable private String selectedICAO;

    // ui addons for flight simulation
    @Nullable private SimulationAddons simAddons;

    /**
     * constructor for LayerPane without parent JPanel
     *
     * @param ui is the {@link UserInterface} where the map is on
     * @param listener is the {@link ActionHandler} which implements
     *                 some listeners and handles user-interactions
     */
    public MapManager(@NotNull UserInterface ui, @NotNull ActionHandler listener, @NotNull TileSource defaultMapSource) {
        this.ui = ui;
        this.mapViewer = defaultMapViewer(ui.getLayerPane(), defaultMapSource);
        this.mapViewer.addMouseListener(listener);
        this.selectedICAO = null;
        this.simAddons = null;
    }

    public void updateMap(@Nullable Vector<Flight> flights, @Nullable ReceiverFrame receiverData) {
        if (flights == null || flights.isEmpty()) {
            clearMap();
            return;
        }
        TreasureMap map = getMapViewer();
        List<MapMarker> mapMarkers = flights.stream()
                .map(flight -> PlaneMarker.fromFlight(flight, getSelectedICAO(), true))
                .collect(Collectors.toList());

        // testing receiver map position
        // painting receiver position on the map
        if (receiverData != null) {
            MapMarkerDot here = new MapMarkerDot("Receiver", new Coordinate(receiverData.getLat(), receiverData.getLon()));
            here.setBackColor(Color.RED);
            here.setColor(Color.BLACK);
            mapMarkers.add(here);
            Coordinate coord = receiverData.getPosition().toCoordinate();
            int cycleCount = 6;
            int km = 20;
            double ldeg = MathUtils.kmToLatDegrees(km);
            MapMarkerCircle mmc;
            for (int i = 0; i < cycleCount; i++) {
                mmc = new MapMarkerCircle(String.valueOf(km * i), coord, ldeg * i);
                mmc.setBackColor(null);
                mmc.setColor(Color.BLACK);
                mapMarkers.add(mmc);
            }
        }

        // setting new map marker list on the map
        map.setMapMarkerList(mapMarkers);
    }

    /**
     * @return the default map viewer component ({@link TreasureMap})
     */
    @NotNull
    public TreasureMap defaultMapViewer(@NotNull Component parent, @NotNull TileSource mapType) {
        TreasureMap viewer = new TreasureMap();
        DefaultMapController mapController = new DefaultMapController(viewer);

        mapController.setMovementMouseButton(1);
        viewer.setBounds(parent.getBounds());
        //viewer.setBorder(BorderFactory.createLineBorder(DEFAULT_BORDER_COLOR.get()));
        viewer.setZoomControlsVisible(false);
        viewer.setTileSource(mapType);
        viewer.setVisible(true);

        return viewer;
    }

    /**
     * clears the map from all
     *      {@link MapMarker}s,
     *      {@link MapPolygon}s and
     *      {@link MapRectangle}s
     */
    public void clearMap() {
        this.mapViewer.removeAllMapMarkers();
        this.mapViewer.removeAllMapPolygons();
        this.mapViewer.removeAllMapRectangles();
    }

    /**
     * creates a 'significance-map' from a significance-{@link HashMap}
     *
     * @param significanceMap is the significance {@link HashMap} with airports, paired with airport reference count
     */
    public void createSignificanceMap(@NotNull final Map<Airport, Integer> significanceMap) {
        List<MapMarker> markers = new ArrayList<>();
        var atomRadius = new AtomicInteger();
        var atomCoord = new AtomicReference<Coordinate>();
        significanceMap.keySet()
                .forEach(ap -> {
                    int lvl = significanceMap.get(ap);
                    if (lvl > 9) {
                        atomRadius.set(lvl / 100);
                        atomCoord.set(ap.pos().toCoordinate());
                        var mark = new MapMarkerCircle(atomCoord.get(), atomRadius.get());
                        mark.setColor(Color.RED);
                        mark.setBackColor(null);
                        markers.add(mark);
                    }
                });
        getMapViewer().setMapMarkerList(markers);
    }

    /**
     * @param marker is the map marker coordinate
     * @param clicked is the clicked coordinate
     * @return true, if clicked coord is equals marker coord, with tolarance
     */
    public boolean isMarkerHit(Coordinate marker, ICoordinate clicked) {
        int zoom = this.ui.getMap().getZoom(), // current zoom
            maxZoom = TreasureMap.MAX_ZOOM; // = 22
        double tolerance = 0.005 * (maxZoom - zoom); // could be a bit too high
        return (clicked.getLat() < marker.getLat() + tolerance &&
                clicked.getLat() > marker.getLat() - tolerance &&
                clicked.getLon() < marker.getLon() + tolerance &&
                clicked.getLon() > marker.getLon() - tolerance);
    }

    /**
     * @param clicked is the marker not to reset
     * @return resetted list of all map markers
     */
    // TODO: 19.08.2022 method for all types resetMarkers
    public List<MapMarker> resetTrackingMarkers(MapMarker clicked) {
        ArrayList<MapMarker> mapMarkers = new ArrayList<>();
        List<MapMarker> markerList = this.ui.getMap().getMapMarkerList();
        PlaneMarker marker;
        int heading;

        for (MapMarker m : markerList) {
            if (m instanceof PlaneMarker dmm) {
                heading = dmm.getHeading();
            } else {
                heading = 0;
            }
            marker = new PlaneMarker(m.getCoordinate(), heading, false, false);
            if (m == clicked) {
                marker.setColor(Color.WHITE);
            } else {
                marker.setColor(Color.BLACK);
            }
            marker.setBackColor(m.getBackColor());
            mapMarkers.add(marker);
        }
        return mapMarkers;
    }

    /**
     *
     *
     * @param dataPoints
     * @param flight
     * @param showPoints
     */
    public void createTrackingMap(Vector<DataPoint> dataPoints, @Nullable Flight flight, boolean showPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return;
        }
        int size = dataPoints.size(), counter = 0, altitude;
        Position dpPos;
        DataPoint lastdp = null;
        PlaneMarker newMarker;
        Position coord1, coord2;
        MapPolygonImpl line;
        Color markerColor;
        List<MapPolygon> polys = new ArrayList<>(size);
        List<MapMarker> markers = new ArrayList<>(size);
        dataPoints = dataPoints.stream()
                .peek(a -> System.out.println(a.flightID() + ", " + a.timestamp()))
                .sorted((a, b) -> a.flightID() == b.flightID()
                        ? Long.compare(a.timestamp(), b.timestamp())
                        : Integer.compare(a.flightID(), b.flightID()))
                .peek(a -> System.out.println(a.flightID() + ", " + a.timestamp()))
                .collect(Collectors.toCollection(Vector::new));
        for (DataPoint dp : dataPoints) {
            dpPos = dp.pos();
            altitude = dp.altitude();
            markerColor = Utilities.colorByAltitude(altitude);
            if (counter++ > 0) {
                // checking if the data points belong to the same flight,
                //          if they are in correct order and
                //          if they make a lon-jump
                if (       dp.flightID() == lastdp.flightID()
                        && dp.timestamp() >= lastdp.timestamp()
                        && noLonJump(lastdp, dp)) {

                    coord1 = dpPos;
                    coord2 = lastdp.pos();
                    line = new MapLine(coord1, coord2, markerColor); // we need a line, so we use one point twice
                    polys.add(line);
                }
            }
            if (showPoints) {
                newMarker = PlaneMarker.fromDataPoint(dp, false, false);
                newMarker.setBackColor(markerColor);
                markers.add(newMarker);
            }
            lastdp = dp;
        }

        if (showPoints) {
            mapViewer.setMapMarkerList(markers);
        }
        mapViewer.setMapPolygonList(polys);
        if (dataPoints.size() == 1 && flight != null) {
            ui.showInfo(flight, dataPoints.get(0));
        }
    }

    public void createSimulationMap(@NotNull Stack<DataPoint> data) {
        if (data.isEmpty()) {
            return;
        }
        Position pos;
        DataPoint dp = data.pop();
        pos = dp.pos();
        List<MapMarker> markers = new ArrayList<>();
        List<MapPolygon> polys  = new ArrayList<>();

        PlaneMarker marker = PlaneMarker.fromPosition(dp.pos(), dp.heading(), true, false);
        markers.add(marker);

        while (!data.isEmpty()) {
            dp = data.pop();
            Color color = Utilities.colorByAltitude(dp.altitude());

            marker = PlaneMarker.fromDataPoint(dp, color, false, false);
            markers.add(marker);

            polys.add(new MapLine(pos, dp.pos(), color));
            pos = dp.pos();
        }
        clearMap();
        mapViewer.setMapMarkerList(markers);
        mapViewer.setMapPolygonList(polys);
    }

    public void createSearchMap(Vector<DataPoint> dataPoints, boolean showAllPoints) {
        if (dataPoints == null || dataPoints.isEmpty()) {
            return;
        }
        DataPoint lastDp = null;
        List<Integer> paintedFlights = new ArrayList<>();
        List<MapMarker> mapMarkers = new ArrayList<>();
        List<MapPolygon> mapPolys = new ArrayList<>();
        Color color; PlaneMarker marker; MapPolygonImpl poly;
        Coordinate currCoord, lastCoord; int flightID;
        for (DataPoint dp : dataPoints) {
            currCoord = dp.pos().toCoordinate();
            color = Utilities.colorByAltitude(dp.altitude());
            flightID = dp.flightID();
            if (lastDp != null) {
                lastCoord = lastDp.pos().toCoordinate();

                if (   lastDp.flightID() == flightID
                    && lastDp.timestamp() < dp.timestamp()
                    && noLonJump(lastDp, dp)) {

                    poly = new MapPolygonImpl(lastCoord, currCoord, lastCoord);
                    poly.setColor(color);
                    mapPolys.add(poly);
                }
            }
            if (showAllPoints || !paintedFlights.contains(flightID)) {
                marker = PlaneMarker.fromDataPoint(dp, true, false);
                marker.setBackColor(color);
                mapMarkers.add(marker);
                if (!showAllPoints) {
                    paintedFlights.add(flightID);
                }
            }
            lastDp = dp;
        }
        mapViewer.setMapMarkerList(mapMarkers);
        mapViewer.setMapPolygonList(mapPolys);
    }

    /**
     * getter for the map viewer ({@link TreasureMap})
     *
     * @return the map viewer ({@link TreasureMap})
     */
    @NotNull
    public TreasureMap getMapViewer() {
        return this.mapViewer;
    }

    /**
     * getter for the current selected ICAO
     *
     * @return the current selected ICAO or null, if there is no
     */
    @Nullable
    public String getSelectedICAO() {
        return this.selectedICAO;
    }

    /**
     * sets the current selected ICAO
     *
     * @param selectedICAO is the current selected ICAO, may be null
     */
    public void setSelectedICAO(@Nullable String selectedICAO) {
        this.selectedICAO = selectedICAO;
    }

    @Nullable
    public SimulationAddons getSimulationAddons() {
        return simAddons;
    }

    public void setSimulationAddons(@Nullable SimulationAddons simAddons) {
        this.simAddons = simAddons;
    }

    /**
     * indicator for a 'lon-jump' (e.g. when the plane goes off the map on the right
     * side and goes further on the left side), would create an ugly line between the points
     * over the whole map
     *
     * @param a is the first {@link DataPoint}
     * @param b is the second {@link DataPoint}
     * @return true if the {@link DataPoint}s do not make such a 'lon-jump', else false
     */
    private boolean noLonJump(@NotNull DataPoint a, @NotNull DataPoint b) {
        double lon0 = a.pos().lon();
        double lon1 = b.pos().lon();
        return (!(lon0 < -90) || !(lon1 > 90)) && (!(lon0 > 90) || !(lon1 < -90));
    }
}
