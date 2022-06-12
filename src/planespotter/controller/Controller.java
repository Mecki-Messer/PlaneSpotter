package planespotter.controller;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import org.openstreetmap.gui.jmapviewer.Coordinate;
import org.openstreetmap.gui.jmapviewer.MapMarkerDot;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.MapMarker;
import planespotter.constants.UserSettings;
import planespotter.constants.ViewType;
import planespotter.constants.Warning;
import planespotter.dataclasses.*;
import planespotter.display.*;
import planespotter.model.*;
import planespotter.model.io.DBOut;
import planespotter.model.io.FileMaster;
import planespotter.statistics.RasterHeatMap;
import planespotter.statistics.Statistics;
import planespotter.throwables.DataNotFoundException;
import planespotter.throwables.IllegalInputException;
import planespotter.throwables.InvalidDataException;
import planespotter.util.Logger;
import planespotter.util.Utilities;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Vector;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static planespotter.constants.DefaultColor.DEFAULT_MAP_ICON_COLOR;
import static planespotter.constants.Sound.SOUND_DEFAULT;
import static planespotter.constants.ViewType.*;

/**
 * @name Controller
 * @author jml04
 * @author Lukas
 * @author Bennet
 * @version 1.1
 *
 * main controller - responsible for connection between model and view
 * has static controller, scheduler, gui, action handler and logger instance
 */
public class Controller {
    // ONLY Controller instance
    private static final Controller mainController;
    // scheduler, contains executor services / thread pools
    private static final Scheduler scheduler;
    // gui action handler (contains listeners)
    private static final ActionHandler actionHandler;
    // only GUI instance
    private static final GUI gui;
    // gui adapter
    private static final GUIAdapter guiAdapter;
    // logger for whole program
    private static Logger logger;

    static {
        scheduler = new Scheduler();
        mainController = new Controller();
        actionHandler = new ActionHandler();
        gui = new GUI(actionHandler);
        guiAdapter = new GUIAdapter(gui);
    }
    // boolean loading is true when something is loading
    private volatile boolean loading;
    // collections for live flights and loaded flights
    public volatile Vector<DataPoint> loadedData;
    public volatile Vector<Flight> liveData;
    // already-clicking/-initialized flag
    private boolean clicking, initialized;
    // hash code
    private final int hashCode;

    /**
     * constructor - private -> only ONE instance ( getter: Controller.getInstance() )
     */
    private Controller() {
        this.hashCode = System.identityHashCode(mainController);
        this.clicking = false;
    }

    /**
     * @return ONE and ONLY controller instance
     */
    public static Controller getInstance() {
        return mainController;
    }

    /**
     * initializes the controller
     */
    private void initialize() {
        if (!this.initialized) {
            logger = new Logger(this);
            logger.log("initializing Controller...", this);
            this.startExecutors();
            logger.sucsessLog("Controller initialized sucsessfully!", this);
            this.initialized = true;
        }
    }

    /**
     * initializes all executors
     * :: -> method reference
     */
    private void startExecutors() {
        if (!this.initialized) {
            logger.log("initializing Executors...", this);

            scheduler.exec(() -> {
                        if (!this.loading) this.loadLiveData();
                    }, "Live-Data PreLoader")
                    .schedule(() -> {
                        if (!this.loading) FileMaster.saveConfig();
                    }, 60, 300)
                    .schedule(() -> {
                        if (!this.loading) {
                            var current = Thread.currentThread();
                            current.setName("Data Inserter");
                            current.setPriority(2);
                            DataLoader.insert(scheduler, 250);
                        }
                    }, 20, 20)
                    .schedule(() -> {
                        System.gc();
                        logger.log("Calling Garbage Collector...", this);
                    }, 10, 10)
                    // loading live date if live map is open
                    .schedule(() -> {
                        if (LiveData.isLive() && !this.isLoading()) {
                            Thread.currentThread().setName("Live Loader");
                            this.loadLiveData();
                        }
                    }, 0, 5);

            logger.sucsessLog("Executors initialized sucsessfully!", this);
        }
    }

    /**
     * starts the program, opens a gui and initializes the controller
     */
    public synchronized void start() {
        this.initialize();
        this.openWindow();
        try {
            while (this.loading) {
                this.wait();
            }
            this.notify();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        this.donePreLoading();
        this.done();
    }

    /**
     * opens a new GUI window as a thread
     */
    private synchronized void openWindow() {
        this.loading = true;
        logger.log("initialising GUI...", gui);
        scheduler.exec(gui, "Planespotter-GUI", false, Scheduler.MID_PRIO, false);
        logger.sucsessLog("GUI initialized sucsessfully!", gui);
        gui.getContainer("window").setVisible(true);
        this.done();
    }

    // TODO evtl. move to ActionHandler
    public synchronized void onExit(final WindowEvent event) {
        int option = JOptionPane.showConfirmDialog(event.getWindow(),
                "Do you really want to exit PlaneSpotter?",
                    "Exit", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (option == JOptionPane.YES_OPTION) {
            logger.infoLog("Shutting down program, please wait...", this);
            LiveData.setLive(false);
            this.setLoading(true);
            FileMaster.saveConfig();
            //DataLoader.insertRemaining(scheduler);
            DataLoader.setEnabled(false);
            while (scheduler.active() > 0) {
                try {
                    this.wait(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                this.notifyAll();
            }
            this.done();
            logger.close();
            scheduler.shutdown();
            System.exit(0);

        }
    }

    /**
     * loads the live data from Fr24 directly into the View
     * @indev
     */
    public synchronized void loadLiveData() {
        this.loading = true;

        this.liveData = LiveData.directLiveData(scheduler);
        var map = gui.getMap();
        var markers = new ArrayList<MapMarker>();
        this.liveData.stream()
                //.filter(Objects::nonNull)
                .map(flight -> {
                    final var pos = flight.dataPoints().get(0).pos();
                    final double lat = pos.lat(),
                                 lon = pos.lon();
                    return new MapMarkerDot(new Coordinate(lat, lon));
                })
                .forEach(m -> {
                    m.setBackColor(DEFAULT_MAP_ICON_COLOR.get());
                    markers.add(m);
                });
        map.removeAllMapMarkers();
        map.setMapMarkerList(markers);
        this.done();
    }

    /**
     * this method is executed when a loading process is done
     * sets this.loading to false
     */
    public void done() {
        this.loading = false;
        if (gui != null) {
            guiAdapter.stopProgressBar();
        }
    }

    /**
     * this method is executed when pre-loading is done
     */
    // TODO auslagern in GUIAdapter
    public void donePreLoading() {
        Utilities.playSound(SOUND_DEFAULT.get());
        gui.loadingScreen.dispose();
        var window = gui.getContainer("window");
        window.setVisible(true);
        window.requestFocus();
    }

    /**
     * creates a GUI-view for a specific view-type
     *
     * @param type is the ViewType, sets the content type for the
     *             created view (e.g. different List-View-Types)
     */
    public synchronized void show(@NotNull ViewType type,
                                  @NotNull final String headText,
                                  @Nullable String... data) {
        var mapManager = gui.getMapManager();
        this.loading = true;
        var dbOut = new DBOut();
        // TODO ONLY HERE: dispose GUI view(s)
        guiAdapter.disposeView();
        gui.setCurrentViewType(type);
        switch (type) {
            case LIST_FLIGHT -> this.showFlightList(dbOut);
            case MAP_LIVE -> this.showLiveMap(headText, mapManager);
            case MAP_FROMSEARCH -> this.showSearchMap(headText, mapManager);
            case MAP_TRACKING -> this.showTrackingMap(headText, mapManager, dbOut, data);
            case MAP_TRACKING_NP -> this.showTrackingMapNoPoints(headText, mapManager, data);
            case MAP_SIGNIFICANCE -> this.showSignificanceMap(headText, mapManager, dbOut);
            case MAP_HEATMAP -> this.showRasterHeatMap(headText, mapManager, dbOut);
        }
        this.done();
        logger.sucsessLog("view loaded!", this);
    }

    /**
     * search method for the GUI-search
     *
     * @param inputs are the inputs in the search fields
     * @param button is the clicked search button, 0 = LIST, 1 = MAP
     */
    // TODO: 24.05.2022 DEBUG PLANE SEARCH
    public void search(String[] inputs, int button) { // TODO button abfragen??
        var gad = guiAdapter;
        this.loading = true;
        try {
            gad.startProgressBar();
            var search = new Search();
            switch (gui.getCurrentSearchType()) {
                case AIRLINE -> {} // TODO implement
                case AIRPORT -> this.searchForAirport(inputs, button, search);
                case FLIGHT -> this.searchForFlight(inputs, button, search);
                case PLANE -> this.searchForPlane(inputs, button, search);
            }
        } catch (DataNotFoundException e) {
            this.handleException(e);
        } finally {
            gad.stopProgressBar();
        }
    }

    /**
     *
     * @param data [0] and [1] must be filled
     */
    public void confirmSettings(String... data) {
        if (data[0] == null || data[1] == null) {
            throw new IllegalArgumentException("Please fill all fields! (with the right params)");
        }
        var us = new UserSettings();
        us.setMaxLoadedData(Integer.parseInt(data[0]));
        var map = UserSettings.getCurrentMapSource();
        switch (data[1]) {
            case "Bing Map" -> map = us.bingMap;
            case "Default Map" -> map = us.tmstMap;
            case "Transport Map" -> map = us.transportMap;
        }
        us.setCurrentMapSource(map);
        gui.getMap().setTileSource(map);
    }

    /**
     * is executed when a map marker is clicked
     *
     * @param point is the clicked map point (no coordinate)
     */
    public synchronized void mapClicked(Point point) {
        var clicked = gui.getMap().getPosition(point);
        switch (gui.getCurrentViewType()) {
            case MAP_LIVE -> this.onLiveClick(clicked);
            case MAP_FROMSEARCH -> this.onClick_all(clicked);
            case MAP_TRACKING -> this.onClick_tracking(clicked);
        }
    }

    /**
     * enters the text in the textfield (use for key listener)
     */
    public void enterText(String text) {
        if (!text.isBlank()) {
            if (text.startsWith("exit")) {
                System.exit(2);
            } else if (text.startsWith("loadlist")) {
                this.show(LIST_FLIGHT, "");
            } else if (text.startsWith("loadmap")) {
                this.show(MAP_LIVE, "");
            } else if (text.startsWith("maxload")) {
                var args = text.split(" ");
                try {
                    int max = Integer.parseInt(args[1]);
                    if (max <= 10000) {
                        new UserSettings().setMaxLoadedData(max);
                        Controller.getLogger().log("maxload changed to " + args[1] + " !", gui);
                    } else {
                        Controller.getLogger().log("Failed! Maximum is 10000!", gui);
                    }
                } catch (NumberFormatException e) {
                    e.printStackTrace();
                }
            } else if (text.startsWith("flightroute") || text.startsWith("fl")) {
                var args = text.split(" ");
                if (args.length > 1) {
                    var id = args[1];
                    this.show(MAP_TRACKING, id);
                }
            }
        }
        var searchTextField = (JTextField) gui.getContainer("searchTextField");
        searchTextField.setText("");
    }

    private void onLiveClick(ICoordinate clickedCoord) {
        if (!this.clicking && LiveData.isLive()) {
            this.clicking = true;
            var map = gui.getMap();
            var markers = map.getMapMarkerList();
            var mapManager = gui.getMapManager();
            var tpl = new TreePlantation();
            var newMarkerList = new ArrayList<MapMarker>();
            boolean markerHit = false;
            int counter = 0;
            DefaultMapMarker newMarker;
            Coordinate markerCoord;
            for (var marker : markers) {
                markerCoord = marker.getCoordinate();
                newMarker = new DefaultMapMarker(markerCoord, 0);
                if (mapManager.isMarkerHit(markerCoord, clickedCoord)) {
                    markerHit = true;
                    var menu = (JPanel) gui.getContainer("menuPanel");
                    this.markerHit(MAP_LIVE, newMarker, counter, null, null, tpl, logger, menu);
                } else {
                newMarker.setBackColor(DEFAULT_MAP_ICON_COLOR.get());
            }
            newMarker.setName(marker.getName());
            newMarkerList.add(newMarker);
            counter++;
            }
            if (markerHit) {
                gui.getMap().setMapMarkerList(newMarkerList);
            }
            this.clicking = false;
        }
    }

    /**
     * is executed when a map marker is clicked and the current is MAP_ALL
     */
    private void onClick_all(ICoordinate clickedCoord) { // TODO aufteilen
        if (!this.clicking) {
            this.clicking = true;
            var markers = gui.getMap().getMapMarkerList();
            var newMarkerList = new ArrayList<MapMarker>();
            Coordinate markerCoord;
            DefaultMapMarker newMarker;
            boolean markerHit = false;
            var bbn = gui.getMapManager();
            var ctrl = Controller.getInstance();
            int counter = 0;
            var data = ctrl.loadedData;
            var dbOut = new DBOut();
            var tpl = new TreePlantation();
            var logger = Controller.getLogger();
            var menu = (JPanel) gui.getContainer("menuPanel");
            // going though markers
            for (var m : markers) {
                markerCoord = m.getCoordinate();
                newMarker = new DefaultMapMarker(markerCoord, 90); // FIXME: 13.05.2022 // FIXME 19.05.2022
                if (bbn.isMarkerHit(markerCoord, clickedCoord)) {
                    markerHit = true;
                    this.markerHit(MAP_FROMSEARCH, newMarker, counter, data, dbOut, tpl, logger, menu);
                } else {
                    newMarker.setBackColor(DEFAULT_MAP_ICON_COLOR.get());
                }
                newMarker.setName(m.getName());
                newMarkerList.add(newMarker);
                counter++;
            }
            if (markerHit) {
                gui.getMap().setMapMarkerList(newMarkerList);
            }
            this.clicking = false;
        }
    }

    private void markerHit(ViewType viewType, DefaultMapMarker marker,
                           int counter, Vector<DataPoint> dataPoints,
                           DBOut dbOut, TreePlantation treePlantation,
                           Logger logger, JPanel menuPanel) {
        switch (viewType) {
            case MAP_FROMSEARCH -> {
                marker.setBackColor(Color.RED);
                menuPanel.setVisible(false);
                int flightID = dataPoints.get(counter).flightID(); // FIXME: 15.05.2022 WAS IST MIT DEM COUNTER LOS
                //  (keine info beim click - flight is null)
                try {
                    var flight = dbOut.getFlightByID(flightID);
                    treePlantation.createFlightInfo(flight, guiAdapter);
                } catch (DataNotFoundException e) {
                    logger.errorLog("flight with the ID " + flightID + " doesn't exist!", this);
                }
            }
            case MAP_LIVE -> {
                marker.setBackColor(Color.RED);
                var flight = this.liveData.get(counter);
                menuPanel.setVisible(false);
                var dps = flight.dataPoints();
                treePlantation.createDataPointInfo(flight, dps.get(0), guiAdapter);
            }
        }
    }

    /**
     *
     * @param clickedCoord is the clicked coordinate
     */
    public void onClick_tracking(ICoordinate clickedCoord) { // TODO aufteilen
        var map = gui.getMap();
        var markers = map.getMapMarkerList();
        Coordinate markerCoord;
        int counter = 0;
        var bbn = gui.getMapManager();
        var ctrl = Controller.getInstance();
        DataPoint dp;
        int flightID;
        Flight flight;
        var tpl = new TreePlantation();
        var dbOut = new DBOut();
        for (var m : markers) {
            markerCoord = m.getCoordinate();
            if (bbn.isMarkerHit(markerCoord, clickedCoord)) {
                gui.getContainer("infoPanel").removeAll();
                dp = ctrl.loadedData.get(counter);
                flightID = dp.flightID();
                try {
                    flight = dbOut.getFlightByID(flightID); // TODO woanders!!!
                    tpl.createDataPointInfo(flight, dp, guiAdapter);
                } catch (DataNotFoundException e) {
                    Controller.getLogger().errorLog("flight with the ID " + flightID + " doesn't exist!", this);
                }
                map.setMapMarkerList(bbn.resetTrackingMarkers(m));
            }
            counter++;
        }
    }

    public void saveFile() {
        gui.setCurrentVisibleRect(gui.getMap().getVisibleRect()); // TODO visible rect beim repainten speichern
        var fileChooser = new MenuModels().fileSaver((JFrame) gui.getContainer("window"));
        new FileMaster().savePlsFile(fileChooser.getSelectedFile(), this);
    }

    public void loadFile() {
        try {
            var fileChooser = new MenuModels().fileLoader((JFrame) gui.getContainer("window"));
            this.loadedData = new FileMaster().loadPlsFile(fileChooser);
            var idList = this.loadedData
                    .stream()
                    .map(DataPoint::flightID)
                    .distinct()
                    .toList();
            int size = idList.size();
            var ids = new String[size];
            int counter = 0;
            for (var id : idList) {
                ids[counter] = id.toString();
                counter++;
            }
            this.show(ViewType.MAP_TRACKING, "Loaded from File", ids);
        } catch (DataNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * handles exceptions
     *
     * @param thr is the throwable (exceptions in the most cases)
     *            which is thrown and will be handled
     */
    public void handleException(final Throwable thr) {
        if (thr instanceof DataNotFoundException) {
            guiAdapter.showWarning(Warning.NO_DATA_FOUND, thr.getMessage());
        } else if (thr instanceof SQLException) {
            guiAdapter.showWarning(Warning.SQL_ERROR);
            thr.printStackTrace();
        } else if (thr instanceof TimeoutException) {
            guiAdapter.showWarning(Warning.TIMEOUT);
        } else if (thr instanceof RejectedExecutionException) {
            guiAdapter.showWarning(Warning.REJECTED_EXECUTION);
        } else if (thr instanceof IllegalInputException) {
            guiAdapter.showWarning(Warning.ILLEGAL_INPUT);
        } else {
            guiAdapter.showWarning(Warning.UNKNOWN_ERROR, thr.getMessage());
            thr.printStackTrace();
        }
    }

    // private methods

    private void  showRasterHeatMap(String heatText, MapManager bbn, DBOut dbOut) {
        try {
            var positions = dbOut.getAllTrackingPositions();
            var viewer = gui.getMap();
            var img = new RasterHeatMap(1f) // TODO replace durch addHeatMap
                    .heat(positions)
                    .createImage();
            bbn.createRasterHeatMap(img, viewer)
                    .recieveMap(viewer, heatText);
        } catch (DataNotFoundException e) {
            this.handleException(e);
        }
    }

    private void showCircleHeatMap(String headText, MapManager bbn, DBOut dbOut) {
        //var liveTrackingBetween = dbOut.getLiveTrackingBetween(10000, 25000);
        //var positions = Utilities.parsePositionVector(liveTrackingBetween);
        //var positionHeatMap = new Statistics().positionHeatMap(positions);
        //var map = bbn.createPrototypeHeatMap(positionHeatMap)
        Vector<Position> positions;
        try {
            positions = dbOut.getAllTrackingPositions();
            var viewer = gui.getMap();
            viewer.setHeatMap(new RasterHeatMap(1f) // TODO: 26.05.2022 addHeatMap
                    .heat(positions)
                    .createImage());
            bbn.recieveMap(viewer, headText);
        } catch (DataNotFoundException e) {
            this.handleException(e);
        }
    }

    private void showSignificanceMap(String headText, MapManager bbn, DBOut dbOut) {
        try {
            var aps = dbOut.getAllAirports();
            var signifMap = new Statistics().airportSignificance(aps);
            var map = bbn.createSignificanceMap(signifMap, gui.getMap());
            bbn.recieveMap(map, headText);
        } catch (DataNotFoundException e) {
            logger.errorLog(e.getMessage(), this);
            e.printStackTrace();
        }
    }

    private void showTrackingMapNoPoints(String headText, MapManager bbn, @Nullable String[] data) {
        try {
            loadedData = new Vector<>();
            var out = new DBOut();
            int flightID = -1;
            if (data.length == 1) {
                assert data[0] != null;
                flightID = Integer.parseInt(data[0]);
                loadedData.addAll(out.getTrackingByFlight(flightID));
            }
            else if (data.length > 1) {
                for (var id : data) {
                    assert id != null;
                    flightID = Integer.parseInt(id);
                    loadedData.addAll(out.getTrackingByFlight(flightID));
                }
            }
            var flight = out.getFlightByID(flightID);
            var flightRoute = bbn.createTrackingMap(this.loadedData, flight, false, guiAdapter);
            bbn.recieveMap(flightRoute, headText);
        } catch (NumberFormatException e) {
            logger.errorLog("NumberFormatException while trying to parse the ID-String! Must be an int!", this);
        } catch (DataNotFoundException e) {
            logger.errorLog(e.getMessage(), this);
        }
    }

    private void showTrackingMap(String headText, MapManager bbn, DBOut dbOut, @Nullable String[] data) {
        try {
            int flightID = -1;
            if (data.length == 1) {
                assert data[0] != null;
                flightID = Integer.parseInt(data[0]);
                loadedData.addAll(dbOut.getTrackingByFlight(flightID));
            }
            else if (data.length > 1) {
                for (var id : data) {
                    assert id != null;
                    flightID = Integer.parseInt(id);
                    loadedData.addAll(dbOut.getTrackingByFlight(flightID));
                }
            }
            if (flightID == -1) {
                throw new InvalidDataException("Flight may not be null!");
            }
            var flight = dbOut.getFlightByID(flightID);
            var trackingMap = bbn.createTrackingMap(this.loadedData, flight, true, guiAdapter);
            bbn.recieveMap(trackingMap, headText);
        } catch (NumberFormatException e) {
            logger.errorLog("NumberFormatException while trying to parse the ID-String! Must be an int!", this);
        } catch (DataNotFoundException e) {
            logger.errorLog(e.getMessage(), this);
        }
    }

    private void showSearchMap(String headText, MapManager bbn) {
        var data = Utilities.parsePositionVector(this.loadedData);
        var viewer = bbn.createLiveMap(data, gui.getMap());
        bbn.recieveMap(viewer, headText);
    }

    private void showLiveMap(final String headText, @NotNull final MapManager mapManager) {
        if (this.liveData == null || this.liveData.isEmpty()) {
            guiAdapter.showWarning(Warning.LIVE_DATA_NOT_FOUND);
            return;
        }
        var data = Utilities.parsePositionVector(this.liveData);
        var viewer = mapManager.createLiveMap(data, gui.getMap());

        LiveData.setLive(true);
        mapManager.recieveMap(viewer, headText);
    }

    private void showFlightList(DBOut dbOut) {
        if (this.loadedData == null || this.loadedData.isEmpty()) {
            guiAdapter.showWarning(Warning.NO_DATA_FOUND);
            return;
        }
        var flights = new ArrayList<Flight>();
        int flightID = -1;
        try {
            var fids = dbOut.getLiveFlightIDs(10000, 25000);
            while (!fids.isEmpty()) {
                flightID = fids.poll();
                flights.add(dbOut.getFlightByID(flightID));
            }
        } catch (DataNotFoundException e) {
            logger.errorLog("flight with  ID " + flightID + " doesn't exist!", this);
            this.handleException(e);
        }
        var treePlant = new TreePlantation();
        treePlant.createTree(treePlant.allFlightsTreeNode(flights), guiAdapter);
    }

    private void searchForPlane(String[] inputs, int button, Search search) throws DataNotFoundException {
        loadedData = search.verifyPlane(inputs);
        var idsNoDupl = new ArrayList<Integer>();
        int flightID;
        for (var dp : loadedData) {
            flightID = dp.flightID();
            if (!idsNoDupl.contains(flightID)) {
                idsNoDupl.add(flightID);
            }
        }
        int size = idsNoDupl.size();
        var ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = idsNoDupl.get(i) + "";
        }
        if (button == 1) {
            var headText = "Plane Search Results:";
            if (!gui.search_planeID.getText().isBlank()) {
                this.show(ViewType.MAP_TRACKING, headText, ids); // ganze route -> nur bei einer id / wird evtl noch entfernt
            } else {
                this.show(ViewType.MAP_FROMSEARCH, headText, ids); // nur letzte data points
            }
        }
    }

    private void searchForFlight(String[] inputs, int button, Search search)
            throws DataNotFoundException {

        loadedData = search.verifyFlight(inputs);
        if (loadedData.size() == 1) {
            var dp = loadedData.get(0);
            if (button == 1) {
                this.show(ViewType.MAP_TRACKING, "Flight Search Results", dp.flightID() + "");
            }
        } else {
            var idsNoDupl = new ArrayList<Integer>();
            int flightID;
            for (var dp : loadedData) {
                flightID = dp.flightID();
                if (!idsNoDupl.contains(flightID)) {
                    idsNoDupl.add(flightID);
                }
            }
            int size = idsNoDupl.size();
            var ids = new String[size];
            for (int i = 0; i < size; i++) {
                ids[i] = idsNoDupl.get(i) + "";
            }
            if (button == 1) {
                this.show(ViewType.MAP_TRACKING, "Flight Search Results", ids);
            }
        } // TODO !!! show soll Datapoints bekommen und nicht fids, das ist eine weitere anfrage;
    }

    private void searchForAirport(String[] inputs, int button, Search search) throws DataNotFoundException {
        loadedData = search.verifyAirport(inputs);
        var idsNoDupl = new ArrayList<Integer>();
        int flightID;
        for (var dp : loadedData) {
            flightID = dp.flightID();
            if (!idsNoDupl.contains(flightID)) {
                idsNoDupl.add(flightID);
            }
        }
        int size = idsNoDupl.size();
        var ids = new String[size];
        for (int i = 0; i < size; i++) {
            ids[i] = idsNoDupl.get(i) + "";
        }
        if (button == 1) {
            this.show(ViewType.MAP_TRACKING_NP, "Flight Search Results", ids);
        }
    }

    /**
     * @return loading flag, true if Controller is loading something
     */
    public final boolean isLoading() {
        return this.loading;
    }

    public void setLoading(boolean b) {
        this.loading = b;
    }

    /**
     * @return main logger
     */
    public static Logger getLogger() {
        return logger;
    }

    /**
     * @return main scheduler
     */
    public static Scheduler getScheduler() {
        return scheduler;
    }

    /**
     * @return main gui
     */
    public static GUI getGUI() {
        return gui;
    }

    /**
     * @return main ActionHandler
     */
    public static ActionHandler getActionHandler() {
        assert actionHandler != null;
        return actionHandler;
    }

    /**
     * @return controller hash code
     */
    @Override
    public int hashCode() {
        return this.hashCode;
    }

}
