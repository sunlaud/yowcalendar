package sample;

import com.sun.javafx.scene.control.skin.DatePickerSkin;
import javafx.application.Application;
import javafx.application.Platform;
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
import javafx.stage.Stage;
import javafx.util.Callback;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.*;

public class Main extends Application {
    public static final String IMAGE_FILENAME = "/tmp/img.png";
    public static final int COLUMNS_IN_CALENDAR = 4;
    public static final boolean FIRST_DOWN_THEN_RIGHT = false;

    private final int year = LocalDate.now().getYear();
    private final Map<LocalDate, String> holidays = getHolidays(year);

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
        Pane calendarPane = getCalendar(COLUMNS_IN_CALENDAR);

        final Button renderBtn = new Button("Render image");
        renderBtn.setOnAction((ActionEvent event) -> {
            renderBtn.setDisable(true);
            renderBtn.setCursor(Cursor.WAIT);

            Task renderImageTask = new Task() {
                @Override
                protected Object call() throws Exception {
                    SnapshotParameters snapshotParameters = new SnapshotParameters();
                    snapshotParameters.setFill(Color.TRANSPARENT);
                    Platform.runLater(() -> {
                        try {
                            WritableImage snapshot = calendarPane.snapshot(snapshotParameters, null);
                            System.out.println("got snapshot");
                            File file = new File(IMAGE_FILENAME);
                            ImageIO.write(SwingFXUtils.fromFXImage(snapshot, null), "png", file);
                            System.out.println("Saved snapshot to " + file);
                            System.out.println("scaleX: " + calendarPane.getScaleX());
                            System.out.println("scaleY: " + calendarPane.getScaleY());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });

                    return null;
                }
            };

            EventHandler<WorkerStateEvent> doneRendering = e -> {
                System.out.println("done: " + e.getEventType() + ": " + e.getSource().getException());
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
        Slider zoomSlider = new Slider(0.5,7,1);
        calendarPane.scaleXProperty().bind(zoomSlider.valueProperty());
        calendarPane.scaleYProperty().bind(zoomSlider.valueProperty());

        HBox controls = new HBox(new Label("Zoom", zoomSlider), renderBtn);
        root.getChildren().add(controls);
        root.getChildren().add(new ScrollPane(calendarPane));

        Scene scene = new Scene(root);
        String cssPath = getClass().getResource("datepicker-calendar-grid.css").toExternalForm();
        scene.getStylesheets().add(cssPath);

        primaryStage.setTitle("Hello World");
        primaryStage.setScene(scene);
        primaryStage.show();
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
        int rowsCount = 12 / colsCount;
        for (int i = 0; i < 12; i++) {
            Node popupContent = getMonthCalendarNode(i);
            if (FIRST_DOWN_THEN_RIGHT) {
                calendarPane.add(popupContent, i / rowsCount, i % rowsCount);
            } else {
                calendarPane.add(popupContent, i % colsCount, i / colsCount);
            }
        }
        return calendarPane;
    }

    private Node getMonthCalendarNode(int monthCountingFromZero) {
        LocalDate date = LocalDate.of(year, monthCountingFromZero + 1, 1);
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
    public  static void dump(Node n) { dump(n, 0); }

    private static void dump(Node n, int depth) {
        for (int i = 0; i < depth; i++) System.out.print("  ");
        System.out.println(n + "(" + n.getTypeSelector() + ")");
        if (n instanceof Parent)
            for (Node c : ((Parent) n).getChildrenUnmodifiable())
                dump(c, depth + 1);
    }
}
