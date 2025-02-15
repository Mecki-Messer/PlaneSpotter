package planespotter.a_test;

/*
import jcuda.Pointer;
import jcuda.Sizeof;
import jcuda.jcurand.JCurand;
import jcuda.jcurand.curandGenerator;
import jcuda.jcurand.curandRngType;
import jcuda.runtime.JCuda;
import jcuda.runtime.cudaMemcpyKind;
*/

import jcuda.driver.*;
import jnr.ffi.annotations.In;
import org.jetbrains.annotations.TestOnly;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartFrame;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import planespotter.constants.Paths;
import planespotter.controller.Controller;
import planespotter.dataclasses.Area;
import planespotter.dataclasses.Position;
import planespotter.model.Statistics;
import planespotter.model.io.DBOut;
import planespotter.throwables.DataNotFoundException;
import planespotter.unused.ANSIColor;
import planespotter.util.*;
import planespotter.util.combine.BitmapMaxCombiner;
import planespotter.util.combine.BitmapMeanCombiner;
import planespotter.util.math.MathUtils;
import sun.misc.Unsafe;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Paper;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@GitIgnore
@TestOnly
public class Test {

    // TEST-MAIN
    public static void main(String[] args) throws Exception {

        int subnetmask = 0xffffff00;
        int addr1 = 0xb7440f21;
        int addr2 = 0xb7440f59;
        int addr3 = 0xb7445759;

        // binary and: subnetmask & address => subnet comparison
        //                                  produces => subnet address
        System.out.println(Integer.toHexString(subnetmask & addr1));
        System.out.println(Integer.toHexString(subnetmask & addr2));
        System.out.println(Integer.toHexString(subnetmask & addr3));

    }

    private static void samplerTest() {
        Sampler sampler = new Sampler(1000, Integer.MAX_VALUE) {
            final Runtime rt = Controller.RUNTIME;

            @Override
            public void sample() {
                // kB
                int totalMemory = (int) rt.totalMemory() / 1000;
                int freeMemory = (int) rt.freeMemory() / 1000;
                int memoryUsage = totalMemory - freeMemory;

                Sample sample = new Sample(memoryUsage, Time.nowMillis());
                addSample(sample);
                System.out.println(sample);
            }
        };
        SamplerChart pane = new SamplerChart(sampler, "Sampler", "Time", "Value", 500, 300);
        ScheduledExecutorService exe = Executors.newScheduledThreadPool(1);
        exe.scheduleAtFixedRate(sampler, 0, 2000, TimeUnit.MILLISECONDS);
        //JFreeChart pane = new Statistics().airportSignificance(200);
        /*DefaultXYDataset xy = new DefaultXYDataset();
        JFreeChart chart = ChartFactory.createTimeSeriesChart("Chart", "Time", "Value", xy);
        ChartPanel pane = new ChartPanel(chart);
        pane.setSize(500, 300);*/
        JFrame frame = new JFrame("Test");
        frame.setSize(pane.getSize());
        frame.add(pane);
        frame.setVisible(true);
    }

    private static void combineBitmapTest() throws FileNotFoundException {
        String path1 = Paths.RESOURCE + "bmphistory\\bmp005\\hist001.bmp",
               path2 = Paths.RESOURCE + "bmphistory\\bmp005\\hist002.bmp";

        Bitmap bmp1 = Bitmap.fromImage(path1), bmp2 = Bitmap.fromImage(path2);
        BitmapMeanCombiner meanCombiner = new BitmapMeanCombiner(bmp1, bmp2);
        Bitmap meanResult = meanCombiner.combine().getResult();
        BitmapMaxCombiner maxCombiner = new BitmapMaxCombiner(bmp1, bmp2);
        Bitmap maxResult = maxCombiner.combine().getResult();
        createTestJFrame("MeanCombiner", meanResult.toImage(false));
        createTestJFrame("MaxCombiner", maxResult.toImage(false));
    }

    private static void interestingAreasBitmap() throws DataNotFoundException {
        Collection<Area> areas = Utilities.calculateInterestingAreas(6, 12, 0);
        byte[][] bytes = new byte[360][180];
        for (int x = 0; x < bytes.length; x++) {
            for (int y = 0; y < bytes[0].length; y++) {
                bytes[x][y] = -128;
            }
        }

        for (Area area : areas) {
            int x = (int) (area.getTopLeft().lon() + 180);
            int y = (int) (area.getTopLeft().lat() + 90);
            bytes[x][y] = 127;
        }
        Bitmap bmp = new Bitmap(bytes);
        createTestJFrame("Interesting Areas", bmp);
    }

    /**
     * Test-Result:
     *      default division: about 0 seconds
     *      MathUtils division: about 3 seconds
     */
    @SuppressWarnings("removal")
    private static void divisionBenchmark() {
        Random seedGen = new Random();
        Random rand = new Random(seedGen.nextLong());
        double[] a = new double[1000000];
        double[] b = new double[1000000];
        double[] res = new double[1000000];
        Arrays.fill(a, rand.nextDouble());
        rand.setSeed(seedGen.nextLong());
        Arrays.fill(b, rand.nextDouble());

        SimpleBenchmark.benchmark(() -> {
            for (int i = 0; i < a.length; i++) {
                res[i] = a[i] / b[i];
            }
        }, "Default Division");

        SimpleBenchmark.benchmark(() -> {
            for (int i = 0; i < a.length; i++) {
                res[i] = MathUtils.divide(a[i], b[i]);
            }
        }, "MathUtils Division");
    }

    private static void cudaTest() {
        String cuFile = Paths.CUDA + "CudaArrayMean.ptx";

        // init
        JCudaDriver.setExceptionsEnabled(true);
        JCudaDriver.cuInit(0);

        // getting CUDA device
        CUdevice device = new CUdevice();
        JCudaDriver.cuDeviceGet(device, 0);

        // creating context
        CUcontext context = new CUcontext();
        JCudaDriver.cuCtxCreate(context, 0, device);

        CUmodule module = new CUmodule();
        JCudaDriver.cuModuleLoad(module, cuFile);

        CUfunction func = new CUfunction();
        JCudaDriver.cuModuleGetFunction(func, module, "mean");
    }

    /*public static void gpuTest() {
        Pointer ptr = Pointer.to(new int[] {1});
        JCuda.cudaMalloc(ptr, Sizeof.INT);
        System.out.println(ptr);
        JCuda.cudaFree(ptr);
    }*/

    public static float[] randomCpu(int n, int seed) {
        Random rnd = new Random(seed);
        float[] data = new float[n];
        for (int i = 0; i < n; i++) {
            data[i] = rnd.nextFloat();
        }
        return data;
    }

    /*public static float[] randomGpu(int n, int seed) {
        JCuda.setExceptionsEnabled(true);
        JCurand.setExceptionsEnabled(true);

        float[] hostData = new float[n];

        Pointer devData = new Pointer();
        JCuda.cudaMalloc(devData, (long) n * Sizeof.FLOAT);

        curandGenerator generator = new curandGenerator();

        JCurand.curandCreateGenerator(generator, curandRngType.CURAND_RNG_PSEUDO_DEFAULT);
        JCurand.curandSetPseudoRandomGeneratorSeed(generator, seed);
        JCurand.curandGenerateUniform(generator, devData, n);
        JCuda.cudaMemcpy(Pointer.to(hostData), devData, (long) n * Sizeof.FLOAT, cudaMemcpyKind.cudaMemcpyDeviceToHost);

        JCurand.curandDestroyGenerator(generator);
        JCuda.cudaFree(devData);

        return hostData;

    }*/

    public static void bitmapTest() throws DataNotFoundException, IOException {

        DBOut dope = DBOut.getDBOut();
        Vector<Position> positions = dope.getAllTrackingPositions();
        long start = Time.nowMillis();
        Bitmap bitmap = Bitmap.fromPosVector(positions, 0.025f);
        Bitmap b2 = Bitmap.fromPosVector(positions, 0.025f);
        BitmapMeanCombiner combiner = new BitmapMeanCombiner(bitmap, b2);
        Bitmap result = combiner
                .combine()
                .getResult();
        System.out.println(result.equals(bitmap));
        System.out.println(result.equals(b2));
        /*Bitmap.writeToCSV(bitmap, "testCsvFile");*/
        System.out.println(Time.elapsedMillis(start));
        //Bitmap.write(bitmap, "RYR.bmp");

    }

    private static void printTest(PrinterJob printer) throws PrinterException {
        printer.setPrintable((g, pageFormat, index) -> {
            pageFormat.setOrientation(PageFormat.LANDSCAPE);
            pageFormat.setPaper(new Paper());
            try {
                g.drawImage(ImageIO.read(new File(Paths.RESOURCE + "bitmap.bmp")), 0, 0, null);
            } catch (IOException e) {
                e.printStackTrace();
                return -1;
            }
            return index;
        });
        printer.print();
    }

    /**
     * something like this could be used when starting the program,
     * as a connection 'pre-check' (also for the map tile URLs),
     * maybe with param 'URL... urls'
     *
     * @throws IOException
     */
    public static void connectTest() throws IOException {
        String spec = "https://data-live.flightradar24.com/";
        URL testUrl = new URL(spec);
        URLConnection conn = testUrl.openConnection();
        conn.setConnectTimeout(5000);
        try {
            conn.connect();
            InetAddress address = InetAddress.getByName(testUrl.getHost());
            System.out.println(address.getHostName() + " reachable: " + address.isReachable(10));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Unsafe getUnsafe() {
        return Utilities.getUnsafe();
    }

    /**
     * unsafe class is able to do 'JVM-actions' like allocating memory or sth. like that
     */
    private static void unsafeTest() {
        Unsafe unsafe = getUnsafe();
        System.out.println(unsafe.addressSize());
        unsafe.allocateMemory(4);

        // this throws a JVM error because of illegal access // or maybe wrong address
        long address = 0x00000001L;
        unsafe.setMemory(address, 1L, (byte) 5);
        System.out.println(unsafe.getByte(address));
    }

    private void testBitmapWrite() throws IOException, DataNotFoundException {
        var positions = DBOut.getDBOut().getAllTrackingPositions();/*new DBOut()
                .getTrackingsWithAirportTag("FRA")
                .stream()
                .map(DataPoint::pos)
                .collect(Collectors.toCollection(Vector::new));*/
        var bmp = Bitmap.fromPosVector(positions, 0.5f);
        //test.createTestJFrame(bmp.toImage());
        // bitmap write & read funktioniert
        Bitmap.write(bmp, new File(Paths.RESOURCE + "bmpBitmap.bmp"));
    }

    /*private void speedChartTest() throws DataNotFoundException {
        var ptps = DBOut.getDBOut().getAllPlanetypesLike("c20");
        var fids = DBOut.getDBOut().getFlightIDsByPlaneTypes(ptps);
        var arr = fids.toArray(new Integer[0]);

        this.testSpeedChartByFlightID(Stream.of(arr).mapToInt(i -> i).toArray());
    }

    private void testSpeedChartByFlightID(@Range(from = 0, to = Integer.MAX_VALUE) int... flightIDs) {
        var dbOut = DBOut.getDBOut();
        var inputDeques = new ArrayDeque<?>[0];
        for (int fid : flightIDs) {
            try {
                int length = inputDeques.length;
                inputDeques = Arrays.copyOf(inputDeques, length + 1);
                var dps = new Vector<>(dbOut.getTrackingByFlight(fid)); // TODO not in for
                inputDeques[length] = (ArrayDeque<?>) Utilities.parseDeque(dps);
            } catch (DataNotFoundException e) {
                e.printStackTrace();
            }
        };
        var stats = new Statistics();
        var speedMap = stats.windSpeed(new Position(90., -180.), new Position(-90., 180.), (Deque<DataPoint>[]) inputDeques);
        var dataset = Statistics.createMapBarDataset(speedMap);
        var chart = ChartFactory.createLineChart("Flight-Groundspeed", "Time", "Speed (km/h)", dataset);
        var frame = new ChartFrame("Speed", chart);
        frame.pack();
        frame.setVisible(true);
    }*/

    private void testChart1() throws DataNotFoundException {
        var stats = new Statistics();
        var airportTags = DBOut.getDBOut().getAllAirportTagsNotDistinct();
        var apStats = stats.onlySignificant(stats.tagCount(airportTags), 900);
        var dataset = Statistics.createBarDataset(apStats);
        DefaultCategoryDataset defaultCategoryDataset = new DefaultCategoryDataset();
        defaultCategoryDataset.addValue(3., "Speed", "t0");
        defaultCategoryDataset.addValue(9., "Speed", "t1");
        defaultCategoryDataset.addValue(16., "Speed", "t2");
        defaultCategoryDataset.addValue(7., "Speed", "t3");
        defaultCategoryDataset.addValue(45., "Altitude", "t0");
        defaultCategoryDataset.addValue(26., "Altitude", "t1");
        defaultCategoryDataset.addValue(51., "Altitude", "t2");
        defaultCategoryDataset.addValue(33., "Altitude", "t3");
        var barChart = ChartFactory.createLineChart("Test-LineChart", "Attributes", "Time", defaultCategoryDataset, PlotOrientation.VERTICAL, true, true, false);
        var frame = new ChartFrame("Test-LineChart", barChart);
        frame.pack();
        frame.setVisible(true);
    }

    private void testAirlineChart() throws DataNotFoundException {
        var stats = new Statistics();
        var airlineTags = DBOut.getDBOut().getAllAirlineTags();
        var airlineStats = stats.onlySignificant(stats.tagCount(airlineTags), 150);
        var dataset = Statistics.createBarDataset(airlineStats);
        var barChart = ChartFactory.createBarChart("Airline Counter", "Airlines", "Count/Significance", dataset, PlotOrientation.HORIZONTAL, true, true, false);
        newChartFrame("Airline Counter", barChart);
    }

    private static void newChartFrame(String title, JFreeChart chart) {
        var frame = new ChartFrame(title, chart);
        frame.pack();
        frame.setVisible(true);
    }

    private void topAirports(int limit) throws DataNotFoundException {
        var stats = new Statistics();
        var airportTags = DBOut.getDBOut().getAllAirportTagsNotDistinct();
        var apStats = stats.onlySignificant(stats.tagCount(airportTags), 500);
        // filtering top airports
        var sortedMap = new HashMap<String, Integer>();
        apStats.entrySet()
                .stream()
                .sorted((a, b) -> Math.max(a.getValue(), b.getValue()))
                .limit(limit)
                .forEach(entry -> sortedMap.put(entry.getKey(), entry.getValue()));
        var dataset = Statistics.createBarDataset(sortedMap);
        var barChart = ChartFactory.createBarChart("Airport-Significance", "Airports", "Flight-Count", dataset, PlotOrientation.HORIZONTAL, true, true, false);
        newChartFrame("Airport Significance", barChart);
    }

    private void testAirportChart(int minCount) throws DataNotFoundException {
        var stats = new Statistics();
        var airportTags = DBOut.getDBOut().getAllAirportTagsNotDistinct();
        var apStats = stats.onlySignificant(stats.tagCount(airportTags), minCount);
        var dataset = Statistics.createBarDataset(apStats);
        var barChart = ChartFactory.createBarChart("Airport-Significance", "Airports", "Flight-Count", dataset, PlotOrientation.HORIZONTAL, true, true, false);
        newChartFrame("Airport Significance", barChart);
    }

    private void processHandleTest() {
        ProcessHandle.allProcesses()
                .forEach(p -> System.out.println(
                        p.pid() + ": " +
                        p.info().user().orElse(null) + ", " +
                        p.info().command().orElse(null)
                ));
    }

    public void bufferedImageTest() {
        var img = new BufferedImage(1920, 1200, BufferedImage.TYPE_INT_RGB);
        Color color;
        int r=1, g=255, b=100;
        var rnd = new Random();
        for (int i = 0; i < 1920; i++) {
            for (int j = 0; j < 1200; j++) {
                if (j < 255 && j > 1) {
                    r = rnd.nextInt(j);
                    g = rnd.nextInt(j);
                    b = rnd.nextInt(j);
                } else {
                    r = rnd.nextInt(255);
                    g = rnd.nextInt(255);
                    b = rnd.nextInt(255);
                }

                color = new Color(r, g, b);
                img.setRGB(i, j, color.getRGB());
            }
        }

        createTestJFrame("Img-Test", img);
    }

    private static <T> void createTestJFrame(String title, T source) {
        var panel = new JPanel();
        if (source instanceof BufferedImage img) {
            var label = new JLabel(new ImageIcon(img));
            panel.setSize(img.getWidth() + 100, img.getHeight() + 100);
            panel.add(label);
        } else if (source instanceof Bitmap bmp) {
            BufferedImage image = bmp.toImage(true);
            ImageIcon icon = Utilities.scale(new ImageIcon(image), bmp.width, bmp.height);
            JLabel lbl = new JLabel(icon);
            lbl.setSize(bmp.width, bmp.height);
            panel.setSize(lbl.getSize());
            panel.add(lbl);
        } else if (source instanceof Component cmp) {
            panel.setSize(cmp.getSize());
            panel.add(cmp);
        }
        var frame = new JFrame(title);
        frame.setSize(panel.getSize());
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.add(panel);
        frame.setVisible(true);
    }

    private void printPositionHeatMap() {
        long startTime = System.currentTimeMillis();
        System.out.println("Loading heat map...");
        System.out.println();

        var statistics = new Statistics();
        Vector<Position> positions;
        try {
            positions = DBOut.getDBOut().getAllTrackingPositions();
        } catch (DataNotFoundException e) {
            e.printStackTrace();
            return;
        }
        //var positionHeatMap = statistics.positionHeatMap(positions);

        //Arrays.stream(positionHeatMap.toString().split(",")).forEach(System.out::println);

        System.out.println();
        long elapsedMillis = System.currentTimeMillis() - startTime;
        System.out.println(ANSIColor.ORANGE.get() + "elapsed time: " +
                           elapsedMillis/1000 + "s" + ANSIColor.RESET.get());
    }

    private void printSystemEnvironment() {
        var sysEnv = System.getenv();
        var envStr = sysEnv.toString();
        var envSubStr = envStr.substring(1, envStr.length() - 1);
        var split = envSubStr.split(",");
        Arrays.stream(split)
                .forEach(System.out::println);
    }

    private void printSystemProperties() {
        var split = System.getProperties()
                .toString()
                .split(",");
        Arrays.stream(split)
                .forEach(System.out::println);
    }

}
