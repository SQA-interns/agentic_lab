package si.konferenca.registration.adapter.out.excel;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import si.konferenca.registration.domain.OptionCategory;
import si.konferenca.registration.domain.Registration;
import si.konferenca.registration.domain.Registration.Consent;
import si.konferenca.registration.domain.Registration.SelectedOption;
import si.konferenca.registration.domain.RegistrationType;
import si.konferenca.registration.domain.TextField;

/** The export workbook: columns, text cells and formula neutralisation (SR-07, SB-05). */
class PoiRegistrationExporterTest {

  private static final Instant ACCEPTED = Instant.parse("2026-10-02T10:15:30.123Z");

  private final PoiRegistrationExporter exporter = new PoiRegistrationExporter();

  private static Registration student(String firstName) {
    return new Registration(
        UUID.fromString("b7c1d2e3-4f50-4a61-8b72-93a4b5c6d7e8"),
        RegistrationType.STUDENT,
        ACCEPTED,
        Map.of(
            TextField.FIRST_NAME, firstName,
            TextField.LAST_NAME, "Košir",
            TextField.EMAIL, "zan@example.org",
            TextField.STUDY_INSTITUTION, "UL",
            TextField.STUDY_PROGRAMME, "RI",
            TextField.STUDENT_ID, "63210001"),
        List.of(
            new SelectedOption("w1", "Delavnica A", OptionCategory.WORKSHOP),
            new SelectedOption("m1", "Kosilo", OptionCategory.MEAL),
            new SelectedOption("w2", "Delavnica B", OptionCategory.WORKSHOP)),
        new Consent("personal-data", "Soglašam.", ACCEPTED));
  }

  private static List<List<Cell>> cells(byte[] workbookBytes) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
      assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
      assertThat(workbook.getSheetName(0)).isEqualTo("Prijave");
      List<List<Cell>> rows = new ArrayList<>();
      for (Row row : workbook.getSheetAt(0)) {
        List<Cell> cells = new ArrayList<>();
        row.forEach(cells::add);
        rows.add(cells);
      }
      return rows;
    }
  }

  private static List<String> texts(List<Cell> row) {
    return row.stream().map(Cell::getStringCellValue).toList();
  }

  @Test
  void sr07_rowHasExactlyTheRegistrationColumnsInOrder() throws IOException {
    List<List<Cell>> rows = cells(exporter.export(List.of(student("Žan"))));

    assertThat(rows).hasSize(2);
    assertThat(texts(rows.get(0)))
        .containsExactly(
            "Številka prijave",
            "Sprejeto",
            "Vrsta prijave",
            "Ime",
            "Priimek",
            "E-pošta",
            "Organizacija / ustanova",
            "Izobraževalna ustanova",
            "Študijski program",
            "Vpisna številka",
            "Delavnice",
            "Dogodki",
            "Obroki",
            "Druge aktivnosti",
            "Soglasje dano");
    assertThat(texts(rows.get(1)))
        .containsExactly(
            "b7c1d2e3-4f50-4a61-8b72-93a4b5c6d7e8",
            "2026-10-02T10:15:30.123Z",
            "STUDENT",
            "Žan",
            "Košir",
            "zan@example.org",
            "",
            "UL",
            "RI",
            "63210001",
            "Delavnica A; Delavnica B",
            "",
            "Kosilo",
            "",
            "2026-10-02T10:15:30.123Z");
  }

  @Test
  void sb05_everyCellIsTextAndNeverAFormula() throws IOException {
    List<List<Cell>> rows = cells(exporter.export(List.of(student("=HYPERLINK(\"http://x\")"))));

    assertThat(rows.get(1))
        .allSatisfy(cell -> assertThat(cell.getCellType()).isEqualTo(CellType.STRING));
    assertThat(rows.get(1).get(3).getStringCellValue()).isEqualTo("'=HYPERLINK(\"http://x\")");
  }

  @ParameterizedTest
  @ValueSource(strings = {"=1+1", "+38640123456", "-1", "@SUM(A1)"})
  void sb05_valueThatCouldBeAFormulaGetsALeadingApostrophe(String value) {
    assertThat(PoiRegistrationExporter.neutralised(value)).isEqualTo("'" + value);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Ana=1", "Žan", "a-b", "ime@example.org", " =1"})
  void valueThatDoesNotStartLikeAFormulaIsUnchanged(String value) {
    assertThat(PoiRegistrationExporter.neutralised(value)).isEqualTo(value);
  }

  @Test
  void sb05_tabAndCarriageReturnAtTheStartAreNeutralisedAndEmptyStaysEmpty() {
    assertThat(PoiRegistrationExporter.neutralised("\t=1")).isEqualTo("'\t=1");
    assertThat(PoiRegistrationExporter.neutralised("\r=1")).isEqualTo("'\r=1");
    assertThat(PoiRegistrationExporter.neutralised("")).isEmpty();
  }

  @Test
  void exportWithoutRegistrationsHasOnlyTheHeadingRow() throws IOException {
    assertThat(cells(exporter.export(List.of()))).hasSize(1);
  }
}
