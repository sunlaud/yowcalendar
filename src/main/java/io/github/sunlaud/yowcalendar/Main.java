package io.github.sunlaud.yowcalendar;

import com.sun.javafx.scene.control.skin.DatePickerSkin;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.concurrent.WorkerStateEvent;
import javafx.embed.swing.SwingFXUtils;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;
import javafx.stage.Stage;
import javafx.util.Callback;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.*;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

import static io.github.sunlaud.yowcalendar.Main.Arragement.RECTANGULAR_FIRST_DOWN;

public class Main extends Application {
    public static final String OUT_IMAGE_FILENAME = "/tmp/calendar_grid.png";
    public static final int COLUMNS_IN_CALENDAR = 6;
    public static final Arragement arragement = Arragement.CORNER_LEFT_BOTTOM;
    //width & height are limited by texture size of videocard or/and bugs in javafx
    //need to reduce size if exception "Requested texture dimensions (14676x18759) require dimensions (14676x0) that exceed
    //maximum texture size (16384)" is thrown. Workaround as switching to software render using option -Dprism.order=sw
    //doesn't work: another exception is thrown: "Unrecognized image loader: null"
    //to print config: -Dprism.verbose=true
    public static final int DESIRED_IMG_WIDTH = 7016 ;/*9888 - 150;*/
    public static final int DESIRED_IMG_HEIGHT = 9933;/*13997 - 150;*/

    public enum Arragement {
        RECTANGULAR_FIRST_DOWN,
        RECTANGULAR_FIRST_RIGHT,
        CORNER_LEFT_BOTTOM
    }

    private static final int YEAR = LocalDate.now().getYear();
    private final Map<LocalDate, String> holidays = getHolidays(YEAR);

    private final Callback<DatePicker, DateCell> dayCellFactory = new Callback<DatePicker, DateCell>() {
        public DateCell call(final DatePicker datePicker) {
            return new DateCell() {
                @Override
                public void updateItem(LocalDate item, boolean empty) {
                    super.updateItem(item, empty);

                    DayOfWeek day = DayOfWeek.from(item);
                    if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
                        getStyleClass().add("weekend");
                    }
                    if (isHoliday(item)) {
                        getStyleClass().add("holiday");
                    }
                }
            };
        }
    };

    @Override
    public void start(Stage primaryStage) throws Exception {
        Scale scale = new Scale();


        Pane calendarPane = getCalendar(COLUMNS_IN_CALENDAR);


        final Button renderBtn = new Button("Save calendar image");
        renderBtn.setOnAction((ActionEvent event) -> {
            renderBtn.setDisable(true);
            renderBtn.setCursor(Cursor.WAIT);


            Task renderImageTask = new Task() {
                @Override
                protected Object call() throws Exception {
                    SnapshotParameters snapshotParameters = new SnapshotParameters();
                    snapshotParameters.setFill(Color.TRANSPARENT);
                    Platform.runLater(() -> {
                        double oldScaleX = scale.getX();
                        double oldScaleY = scale.getY();
                        try {
                            scaleToFit(calendarPane, scale, DESIRED_IMG_WIDTH, DESIRED_IMG_HEIGHT);
                            WritableImage snapshot = calendarPane.snapshot(snapshotParameters, null);
                            System.out.println("got snapshot, saving...");
                            File file = new File(OUT_IMAGE_FILENAME);
                            ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file);
                            System.out.println("Saved snapshot to " + file);
                            System.out.println("scaleX: " + calendarPane.getScaleX());
                            System.out.println("scaleY: " + calendarPane.getScaleY());
                        } catch (IOException e) {
                            e.printStackTrace();
                        } finally {
                            scale.setX(oldScaleX);
                            scale.setY(oldScaleY);
                        }
                    });
                    return null;
                }
            };

            EventHandler<WorkerStateEvent> doneRendering = e -> {
                System.out.println("done: " + e.getEventType() + ", exception: " + e.getSource().getException());
                renderBtn.setDisable(false);
                renderBtn.setCursor(Cursor.DEFAULT);
            };
            renderImageTask.setOnSucceeded(doneRendering);
            renderImageTask.setOnFailed(doneRendering);
            renderImageTask.setOnCancelled(doneRendering);


            Thread th = new Thread(renderImageTask);
            th.setDaemon(true);
            th.start();
        });

        VBox root = new VBox();
//        Slider zoomSlider = new Slider(0.3,7,1.53);
        //calendarPane.scaleXProperty().bind(zoomSlider.valueProperty());
        //calendarPane.scaleYProperty().bind(zoomSlider.valueProperty());

        Slider transparencySlider = new Slider(0.0,1.0,0.15);
        transparencySlider.setMinWidth(200);
        transparencySlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            //TODO: extract current r,g,b values instead of hardcoded 255
            String backgroundCss = String.format("-fx-background-color: rgba(255,255,255,%s)", newValue);
            calendarPane.lookupAll(".month-calendar").forEach(node -> node.setStyle(backgroundCss));
        });
        Label transparencyValue = new Label();
        transparencyValue.textProperty().bind(transparencySlider.valueProperty().asString("%.2f"));


        final Button reloadCssBtn = new Button("Reload CSS");

        HBox controls = new HBox(
                /*new Label("Zoom", zoomSlider), */
                renderBtn,
                reloadCssBtn,
                new Label("Transparency: ", transparencySlider),
                transparencyValue
        );
        root.getChildren().add(controls);
        ScrollPane scrollPane = new ScrollPane(calendarPane);
        scrollPane.setPannable(true);
        root.getChildren().add(scrollPane);

        Scene scene = new Scene(root);
        reloadStyles(scene);


//        scale.setPivotX(50);
//        scale.setPivotY(50);


        calendarPane.getTransforms().add(scale);
        calendarPane.setOnScroll(event -> {
            double minScale = getScaleToFit(calendarPane, 200, 200);
            double scaleDelta = 0.1 * (event.getDeltaY() < 0 ? -1 : 1);
            scale.setX(Double.max(scale.getX() + scaleDelta, minScale));
            scale.setY(Double.max(scale.getY() + scaleDelta, minScale));
        });

        reloadCssBtn.setOnAction((ActionEvent event) -> reloadStyles(scene));

        primaryStage.setTitle("Hello World");
        primaryStage.setScene(scene);
        primaryStage.show();

        Path cssFilePath = Paths.get(getClass().getResource("datepicker-calendar-grid.css").getPath());
        watchCssChange(cssFilePath, scene);
    }



    private void scaleToFit(Pane calendarPane, Scale scale, Number width, Number height) {
        double scaleToFit = getScaleToFit(calendarPane, width, height);
        scale.setX(scaleToFit);
        scale.setY(scaleToFit);
    }

    private double getScaleToFit(Pane pane, Number width, Number height) {
        double scaleX = pane.getWidth() / width.doubleValue();
        double scaleY = pane.getHeight() / height.doubleValue();
        return 1 / Double.max(scaleX, scaleY);
    }

    private void reloadStyles(Scene scene) {
        ObservableList<String> stylesheets = scene.getStylesheets();
        stylesheets.clear();
        URL cssUrl = getClass().getResource("datepicker-calendar-grid.css");
        stylesheets.add(cssUrl.toExternalForm());
        System.out.println("css reloaded");
    }


    public static void main(String[] args) {
        Locale locale = Locale.forLanguageTag("uk-UA");
        System.out.println("locale=" + locale);
        Locale.setDefault(locale);
        launch(args);
    }

    private Pane getCalendar(int colsCount) {
        GridPane calendarPane  = new GridPane();
        calendarPane.setHgap(20);
        calendarPane.setVgap(20);

        int rowsCount;
        switch (arragement) {
            case CORNER_LEFT_BOTTOM:
                rowsCount = 12 - colsCount + 1;
                for (int i = 0; i < rowsCount; i++) {
                    Node popupContent = getMonthCalendarNode(i);
                    calendarPane.add(popupContent, 0, i);
                }
                for (int i = 1; i < colsCount; i++) {
                    Node popupContent = getMonthCalendarNode(colsCount + i);
                    calendarPane.add(popupContent, i, rowsCount - 1);
                }
                break;

            case RECTANGULAR_FIRST_DOWN:
            case RECTANGULAR_FIRST_RIGHT:
            default:
                rowsCount = 12 / colsCount;
                for (int i = 0; i < 12; i++) {
                    Node popupContent = getMonthCalendarNode(i);
                    if (arragement == RECTANGULAR_FIRST_DOWN) {
                        calendarPane.add(popupContent, i / rowsCount, i % rowsCount);
                    } else {
                        calendarPane.add(popupContent, i % colsCount, i / colsCount);
                    }
                }
        }
        Label yearLabel = new Label(String.valueOf(YEAR));
        yearLabel.getStyleClass().add("year-label");
        return new VBox(yearLabel, calendarPane);
    }

    private Node getMonthCalendarNode(int monthCountingFromZero) {
        LocalDate date = LocalDate.of(YEAR, monthCountingFromZero + 1, 1);
        DatePicker datePicker = new DatePicker(date);
        datePicker.setDayCellFactory(dayCellFactory);

        DatePickerSkin datePickerSkin = new DatePickerSkin(datePicker);
        Node popupContent = datePickerSkin.getPopupContent();
        // force a css layout pass to ensure that lookup calls work
        popupContent.applyCss();
        Node grid = popupContent.lookup(".calendar-grid");
        Label name = new Label(date.getMonth().getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()));
        VBox monthCal = new VBox(name, grid);
        monthCal.getStyleClass().add("month-calendar");
        dump(monthCal);
        return monthCal;
//        return popupContent;
    }

    private boolean isHoliday(LocalDate day) {
        if (holidays.containsKey(day)) {
            return true;
        }
        //FIXME error lurks here if several holidays fall to same weekend
        //TODO pre-calculate non-working days in advance: both faster and correct
        if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
            return holidays.containsKey(day.minusDays(1)) || holidays.containsKey(day.minusDays(2));
        }
        if (day.getDayOfWeek() == DayOfWeek.TUESDAY) {
            return holidays.containsKey(day.minusDays(1)) && holidays.containsKey(day.minusDays(2));
        }
        return false;
    }

    private Map<LocalDate, String> getHolidays(int year) {
        Map<LocalDate, String> holidays = new HashMap<>();
        holidays.put(LocalDate.of(year, Month.JANUARY, 1), "Новий рік");
        holidays.put(LocalDate.of(year, Month.JANUARY, 7), "Різдво Христове");
        holidays.put(LocalDate.of(year, Month.MARCH, 8), "Міжнародний жіночий день");
        //TODO calculate easter
        LocalDate easter = LocalDate.of(year, Month.APRIL, 16);  //"змінне"
        holidays.put(easter, "Пасха (Великдень)");
        LocalDate trinity = easter.plusDays(49); //"змінне: Великдень + 49 днів"
        holidays.put(trinity, "Трійця");
        holidays.put(LocalDate.of(year, Month.MAY, 1), "День міжнародної солідарності трудящих");
        holidays.put(LocalDate.of(year, Month.MAY, 2), "День міжнародної солідарності трудящих");
        holidays.put(LocalDate.of(year, Month.MAY, 9), "День перемоги над нацизмом у Другій світовій війні");
        holidays.put(LocalDate.of(year, Month.JUNE, 28), "День Конституції України");
        holidays.put(LocalDate.of(year, Month.AUGUST, 24), "День Незалежності України");
        holidays.put(LocalDate.of(year, Month.OCTOBER, 14), "День захисника України");
        return Collections.unmodifiableMap(holidays);
    }

    /** dump node content to console for debug */
    public  static void dump(Node n) {
        //disable dump when not used to debug, as it clutters console
        //dump(n, 0);
    }

    private static void dump(Node n, int depth) {
        for (int i = 0; i < depth; i++) System.out.print("  ");
        System.out.println(n + "(" + n.getTypeSelector() + ")");
        if (n instanceof Parent)
            for (Node c : ((Parent) n).getChildrenUnmodifiable())
                dump(c, depth + 1);
    }





    private void watchCssChange(Path cssFilePath, Scene scene) {
        System.out.println("watching for file changes: " + cssFilePath);
        Path watchedDir = cssFilePath.getParent();
        Path watchedFileName = cssFilePath.getFileName();
        Task<Void> watchTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                try (final WatchService watchService = FileSystems.getDefault().newWatchService()) {
                    System.out.println("watching for changes in dir: " + watchedDir);
                    watchedDir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY);
                    while (true) {
                        final WatchKey wk = watchService.take();
                        for (WatchEvent<?> event : wk.pollEvents()) {
                            //we only register "ENTRY_MODIFY" so the context is always a Path.
                            final Path changedFilename = (Path) event.context();
                            System.out.println(changedFilename);
                            if (changedFilename.toString().startsWith(watchedFileName.toString())) {
                                System.out.println("looks like CSS file has changed");
                                Platform.runLater(() -> reloadStyles(scene));
                            }
                        }
                        // reset the key
                        boolean valid = wk.reset();
                        if (!valid) {
                            System.err.println("Key has been unregistered");
                        }
                    }
                } catch (InterruptedException | IOException e) {
                    e.printStackTrace();
                }
                return null;
            }
        };
        Thread thread = new Thread(watchTask);
        thread.setDaemon(true);
        thread.start();
    }

}
