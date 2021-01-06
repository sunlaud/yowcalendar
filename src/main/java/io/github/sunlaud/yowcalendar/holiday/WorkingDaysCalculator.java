package io.github.sunlaud.yowcalendar.holiday;

import lombok.NonNull;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

public class WorkingDaysCalculator {
    private static final DateTimeFormatter DAY_MONTH_FORMAT_FOR_CSV = DateTimeFormatter.ofPattern("dMMM", Locale.forLanguageTag("ru-RU"));
    private final int year;
    private final Map<LocalDate, String> holidays;

    public WorkingDaysCalculator(int year) {
        this.year = year;
        this.holidays = getHolidays();
    }

    public boolean isHoliday(@NonNull LocalDate day) {
        if (holidays.containsKey(day)) {
            return true;
        }
        //TODO pre-calculate non-working days in advance: both faster and correct
        if (day.getDayOfWeek() == DayOfWeek.MONDAY) {
            return holidays.containsKey(day.minusDays(1)) || holidays.containsKey(day.minusDays(2));
        }
        //if both weekends are holidays, tuesday is non-working day (as well as monday)
        if (day.getDayOfWeek() == DayOfWeek.TUESDAY) {
            return holidays.containsKey(day.minusDays(3)) && holidays.containsKey(day.minusDays(2));
        }
        return false;
    }

    private Map<LocalDate, String> getHolidays() {
        Map<LocalDate, String> holidays = new HashMap<>();
        //"constant" holidays
        holidays.put(LocalDate.of(year, Month.JANUARY, 1), "Новий рік");
        holidays.put(LocalDate.of(year, Month.JANUARY, 7), "Різдво Христове");
        holidays.put(LocalDate.of(year, Month.MARCH, 8), "Міжнародний жіночий день");
        holidays.put(LocalDate.of(year, Month.MAY, 1), "День праці");
        holidays.put(LocalDate.of(year, Month.MAY, 9), "День перемоги над нацизмом у Другій світовій війні");
        holidays.put(LocalDate.of(year, Month.JUNE, 28), "День Конституції України");
        holidays.put(LocalDate.of(year, Month.AUGUST, 24), "День Незалежності України");
        holidays.put(LocalDate.of(year, Month.OCTOBER, 14), "День захисників України");
        holidays.put(LocalDate.of(year, Month.DECEMBER, 25), "Різдво Христове");

        //"variable" holidays
        LocalDate easter = getEasterDate();
        holidays.put(easter, "Пасха (Великдень)");
        holidays.put(easter.plusDays(49), "Трійця"); //always + 49 days to easter
        return Collections.unmodifiableMap(holidays);
    }

    private LocalDate getEasterDate() {
        try (Scanner scanner = new Scanner(getClass().getClassLoader().getResourceAsStream("easter_ru_RU.csv"))) {
            scanner.useDelimiter("\\s*[,\n]\\s*");
            scanner.nextLine(); //skip header
            while (scanner.hasNext()) {
                int year = scanner.nextInt();
                MonthDay easter = parseMonthDay(scanner.next());
                if (year == this.year) {
                    return easter.atYear(year);
                }
            }
        }
        throw new IllegalStateException("No easter date found for year " + year);
    }

    private MonthDay parseMonthDay(String montDayString) {
        return MonthDay.parse(montDayString.replaceAll("\\s","").toLowerCase(), DAY_MONTH_FORMAT_FOR_CSV);
    }
}
