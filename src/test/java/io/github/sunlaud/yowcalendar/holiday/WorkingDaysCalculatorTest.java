package io.github.sunlaud.yowcalendar.holiday;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class WorkingDaysCalculatorTest {

    @CsvSource(delimiter = '=', value = {
            "2016-05-01 = true", //easter + labor day
            "2016-05-02 = true", //because of easter/labor day
            "2016-05-03 = false", //two holidays on same weekend, but we have only one non-working day, but not two
            "2019-04-28 = true", //easter
            "2019-04-29 = true", //because of easter
            "2019-06-16 = true", //trinity
            "2019-06-17 = true", //because of trinity
            "2019-01-01 = true",
            "2019-01-02 = false",
            "2018-12-25 = true",
            "2018-12-31 = false",
            "2019-10-14 = true",
            "2019-03-08 = true",
            "2019-03-09 = false",
            "2021-05-01 = true", //labor day
            "2021-05-02 = true", //easter
            "2021-05-03 = true", //easter
            "2021-05-04 = true", //if both weekends are holidays, tuesday is non-working day (as well as monday)
    })
    @ParameterizedTest
    void checksIfWorkingDay(String dateAsString, boolean expectedIsWorkingDay) {
        //GIVEN
        LocalDate date = LocalDate.parse(dateAsString);
        WorkingDaysCalculator sut = new WorkingDaysCalculator(date.getYear());

        //WHEN
        boolean actualIsHoliday = sut.isHoliday(date);

        //THEN
        assertThat(actualIsHoliday).as("%s is holiday", date).isEqualTo(expectedIsWorkingDay);
    }
}