package com.example.exam9;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.CategoryChart;
import org.knowm.xchart.CategoryChartBuilder;
import org.knowm.xchart.style.Styler;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class ExcelDataService {

    private final String resourcePath = "src/main/resources/files/financial_data.xlsx"; // Путь к файлу Excel

    public ExcelDataService() {
        // Проверяем существование файла, если не существует - создаем новый
        try {
            File file = new File(resourcePath);
            if (!file.exists()) {
                Files.createDirectories(Paths.get("src/main/resources/files")); // Создаем директорию, если она не существует
                Workbook workbook = new XSSFWorkbook();
                try (FileOutputStream fileOut = new FileOutputStream(file)) {
                    workbook.write(fileOut);
                }
                workbook.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String readExcelData(String sheetName) {
        StringBuilder data = new StringBuilder();

        try (InputStream file = new FileInputStream(new File(resourcePath));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheet(sheetName);

            if (sheet == null) {
                return "Лист с названием '" + sheetName + "' не найден в Excel файле.";
            }

            data.append("<pre>\n");
            data.append(String.format("%-10s %-10s %-10s %-10s %-10s\n", "Месяц", "Доход (€)", "Расход (€)", "Прибыль (€)", "КПН (€)"));

            DecimalFormat decimalFormat = new DecimalFormat("#.##"); // Форматирование чисел

            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() == 0) continue; // Пропускаем заголовок

                StringBuilder rowData = new StringBuilder();
                Iterator<Cell> cellIterator = row.cellIterator();
                while (cellIterator.hasNext()) {
                    Cell cell = cellIterator.next();
                    switch (cell.getCellType()) {
                        case STRING:
                            rowData.append(String.format("%-10s", cell.getStringCellValue()));
                            break;
                        case NUMERIC:
                            double cellValue = cell.getNumericCellValue();
                            String formattedValue = decimalFormat.format(cellValue); // Форматирование чисел
                            rowData.append(String.format("%-10s", formattedValue));
                            break;
                        default:
                            rowData.append(String.format("%-10s", ""));
                            break;
                    }
                    rowData.append(" ");
                }
                data.append(rowData.toString().trim()).append("\n");
            }

            data.append("</pre>");

        } catch (IOException e) {
            e.printStackTrace();
            return "Ошибка при чтении данных из Excel файла.";
        }

        return data.toString();
    }

    public List<String> getSheetNames() {
        List<String> sheetNames = new ArrayList<>();
        try (InputStream file = new FileInputStream(new File(resourcePath))) {
            Workbook workbook = new XSSFWorkbook(file);
            int numberOfSheets = workbook.getNumberOfSheets();
            for (int i = 0; i < numberOfSheets; i++) {
                sheetNames.add(workbook.getSheetName(i));
            }
            workbook.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sheetNames;
    }

    public String addDataToExistingSheet(String sheetName, String month, double income, double expense, double profit, double kpn) {
        try (FileInputStream fileInputStream = new FileInputStream(new File(resourcePath));
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                return "Лист с названием '" + sheetName + "' не найден в Excel файле.";
            }

            int rowCount = sheet.getPhysicalNumberOfRows();
            Row newRow = sheet.createRow(rowCount);

            newRow.createCell(0).setCellValue(month);
            newRow.createCell(1).setCellValue(income);
            newRow.createCell(2).setCellValue(expense);
            newRow.createCell(3).setCellValue(profit);
            newRow.createCell(4).setCellValue(kpn);

            // Перезаписываем файл с новыми данными
            try (FileOutputStream fileOutputStream = new FileOutputStream(new File(resourcePath))) {
                workbook.write(fileOutputStream);
                return "Данные успешно добавлены в существующую компанию '" + sheetName + "'.";
            }

        } catch (IOException e) {
            e.printStackTrace();
            return "Ошибка при добавлении данных в существующую компанию в Excel файле.";
        }
    }

    public String addDataToNewSheet(String sheetName, String month, double income, double expense, double profit, double kpn) {
        try (FileInputStream fileInputStream = new FileInputStream(new File(resourcePath));
             Workbook workbook = new XSSFWorkbook(fileInputStream)) {

            // Проверяем, существует ли уже лист с таким именем
            if (workbook.getSheet(sheetName) != null) {
                return "Компания с именем '" + sheetName + "' уже существует. Пожалуйста, выберите другое имя.";
            }

            // Создаем новый лист
            Sheet sheet = workbook.createSheet(sheetName);

            // Создаем строку заголовка
            Row headerRow = sheet.createRow(0);
            headerRow.createCell(0).setCellValue("Месяц");
            headerRow.createCell(1).setCellValue("Доход (€)");
            headerRow.createCell(2).setCellValue("Расход (€)");
            headerRow.createCell(3).setCellValue("Прибыль (€)");
            headerRow.createCell(4).setCellValue("КПН (€)");

            // Добавляем данные в первую строку данных
            Row newRow = sheet.createRow(1);
            newRow.createCell(0).setCellValue(month);
            newRow.createCell(1).setCellValue(income);
            newRow.createCell(2).setCellValue(expense);
            newRow.createCell(3).setCellValue(profit);
            newRow.createCell(4).setCellValue(kpn);

            // Сохраняем изменения в файл
            try (FileOutputStream fileOutputStream = new FileOutputStream(new File(resourcePath))) {
                workbook.write(fileOutputStream);
            }

            return "Новая компания '" + sheetName + "' успешно создана и данные добавлены.";

        } catch (IOException e) {
            e.printStackTrace();
            return "Ошибка при создании новой компании в Excel файле.";
        }
    }

    public void createFinancialChart(String sheetName) {
        try (InputStream file = new FileInputStream(new File(resourcePath));
             Workbook workbook = new XSSFWorkbook(file)) {

            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet == null) {
                System.out.println("Лист с названием '" + sheetName + "' не найден в Excel файле.");
                return;
            }

            List<Double> incomes = new ArrayList<>();
            List<Double> expenses = new ArrayList<>();
            List<Double> profits = new ArrayList<>();
            List<Double> kpns = new ArrayList<>();
            List<String> months = new ArrayList<>();

            Iterator<Row> rowIterator = sheet.iterator();
            while (rowIterator.hasNext()) {
                Row row = rowIterator.next();
                if (row.getRowNum() == 0) continue; // Пропускаем заголовок

                // Обработка данных, проверка на null и корректность
                if (row.getCell(0) != null && row.getCell(0).getCellType() == CellType.STRING) {
                    months.add(row.getCell(0).getStringCellValue());
                }
                if (row.getCell(1) != null && row.getCell(1).getCellType() == CellType.NUMERIC) {
                    incomes.add(row.getCell(1).getNumericCellValue());
                }
                if (row.getCell(2) != null && row.getCell(2).getCellType() == CellType.NUMERIC) {
                    expenses.add(row.getCell(2).getNumericCellValue());
                }
                if (row.getCell(3) != null && row.getCell(3).getCellType() == CellType.NUMERIC) {
                    profits.add(row.getCell(3).getNumericCellValue());
                }
                if (row.getCell(4) != null && row.getCell(4).getCellType() == CellType.NUMERIC) {
                    kpns.add(row.getCell(4).getNumericCellValue());
                }
            }

            // Построение графика
            CategoryChart chart = new CategoryChartBuilder()
                    .width(800).height(600)
                    .title("Финансовые данные компании " + sheetName)
                    .xAxisTitle("Месяц").yAxisTitle("Сумма (€)")
                    .build();

            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideNW);
            chart.addSeries("Доход", months, incomes);
            chart.addSeries("Расход", months, expenses);
            chart.addSeries("Прибыль", months, profits);
            chart.addSeries("КПН", months, kpns);

            // Проверяем, существует ли директория для сохранения графика
            File chartDir = new File("src/main/resources/files");
            if (!chartDir.exists()) {
                chartDir.mkdirs();
            }

            // Сохранение графика в файл
            String chartPath = "src/main/resources/files/chart.png";
            BitmapEncoder.saveBitmap(chart, chartPath, BitmapEncoder.BitmapFormat.PNG);

        } catch (IOException e) {
            e.printStackTrace();
            System.out.println("Ошибка при создании графика.");
        }
    }
}
