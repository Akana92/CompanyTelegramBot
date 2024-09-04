package com.example.exam9;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialBot extends TelegramLongPollingBot {

    private final String botUsername = "ArkExamTestBot"; // Укажите имя вашего бота
    private final String botToken = "6890383830:AAEKwtmE-iefmBu5ebaXsAjvKhrSUN9Enro"; // Укажите токен вашего бота

    private final Map<Long, UserState> userStates = new HashMap<>();
    private final ExcelDataService excelDataService;
    // Хранение последнего состояния данных для каждого листа
    private final Map<String, String> lastKnownData = new HashMap<>();

    public FinancialBot(ExcelDataService excelDataService) {
        this.excelDataService = excelDataService;
        // Инициализация lastKnownData при создании бота
        for (String sheetName : excelDataService.getSheetNames()) {
            lastKnownData.put(sheetName, excelDataService.readExcelData(sheetName));
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public String getBotToken() {
        return botToken;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            if (userStates.containsKey(chatId)) {
                handleUserInput(chatId, messageText);
            } else {
                if (messageText.equals("/start")) {
                    showMainMenu(chatId);
                }
            }
        } else if (update.hasCallbackQuery()) {
            String callbackData = update.getCallbackQuery().getData();
            long chatId = update.getCallbackQuery().getMessage().getChatId();

            if (callbackData.startsWith("show_chart_")) {
                String sheetName = callbackData.replace("show_chart_", "");
                sendFinancialChartFromExcel(chatId, sheetName);
            } else {
                switch (callbackData) {
                    case "read":
                        showSheetSelection(chatId, "read_sheet_");
                        break;
                    case "add":
                        showAddOptions(chatId);
                        break;
                    case "add_to_existing":
                        startAddingToExistingCompany(chatId);
                        break;
                    case "add_new_company":
                        startNewCompanyCreation(chatId);
                        break;
                    default:
                        if (callbackData.startsWith("read_sheet_")) {
                            String sheetName = callbackData.replace("read_sheet_", "");
                            sendFinancialDataFromExcel(chatId, sheetName);
                        } else if (callbackData.startsWith("add_sheet_")) {
                            String sheetName = callbackData.replace("add_sheet_", "");
                            startAddingDataToExistingCompany(chatId, sheetName);
                        }
                        break;
                }
            }
        }
    }

    private void sendFinancialDataFromExcel(long chatId, String sheetName) {
        try {
            String data = excelDataService.readExcelData(sheetName); // Чтение данных из Excel

            if (data.isEmpty()) {
                sendMessage(chatId, "Нет данных для отображения.");
                return;
            }

            sendFormattedMessage(chatId, data); // Отправка данных в текстовом виде

            // Добавление кнопки для отображения графика
            SendMessage message = new SendMessage();
            message.setChatId(String.valueOf(chatId));
            message.setText("Хотите увидеть данные на графике?");

            InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
            List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();
            List<InlineKeyboardButton> rowInline = new ArrayList<>();

            InlineKeyboardButton chartButton = new InlineKeyboardButton();
            chartButton.setText("Показать график");
            chartButton.setCallbackData("show_chart_" + sheetName);
            rowInline.add(chartButton);

            rowsInline.add(rowInline);
            keyboardMarkup.setKeyboard(rowsInline);
            message.setReplyMarkup(keyboardMarkup);

            execute(message); // Отправляем сообщение с кнопкой

        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при обработке данных.");
        }
    }

    private void sendFinancialChartFromExcel(long chatId, String sheetName) {
        try {
            excelDataService.createFinancialChart(sheetName); // Создаем график

            // Проверяем, существует ли файл графика
            String chartPath = "src/main/resources/files/chart.png";
            File chartFile = new File(chartPath);

            if (!chartFile.exists()) {
                sendMessage(chatId, "График не был создан. Проверьте данные и попробуйте снова.");
                return;
            }

            // Отправка графика пользователю
            SendPhoto sendPhotoRequest = new SendPhoto();
            sendPhotoRequest.setChatId(String.valueOf(chatId));
            sendPhotoRequest.setPhoto(new InputFile(chartFile));
            execute(sendPhotoRequest);

        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendMessage(chatId, "Ошибка при отправке графика.");
        }
    }

    private void handleUserInput(long chatId, String input) {
        UserState userState = userStates.get(chatId);

        switch (userState.getCurrentStep()) {
            case WAITING_FOR_COMPANY_NAME:
                userState.setCompanyName(input);
                userState.setNewCompany(true); // Устанавливаем флаг новой компании
                userState.setCurrentStep(UserState.Step.WAITING_FOR_MONTH);
                sendMessage(chatId, "Введите месяц:");
                break;
            case WAITING_FOR_MONTH:
                userState.setMonth(input);
                userState.setCurrentStep(UserState.Step.WAITING_FOR_INCOME);
                sendMessage(chatId, "Введите доход (€):");
                break;
            case WAITING_FOR_INCOME:
                try {
                    userState.setIncome(Double.parseDouble(input));
                    userState.setCurrentStep(UserState.Step.WAITING_FOR_EXPENSE);
                    sendMessage(chatId, "Введите расход (€):");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите корректное число для дохода.");
                }
                break;
            case WAITING_FOR_EXPENSE:
                try {
                    userState.setExpense(Double.parseDouble(input));
                    userState.setCurrentStep(UserState.Step.WAITING_FOR_PROFIT);
                    sendMessage(chatId, "Введите прибыль (€):");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите корректное число для расхода.");
                }
                break;
            case WAITING_FOR_PROFIT:
                try {
                    userState.setProfit(Double.parseDouble(input));
                    userState.setCurrentStep(UserState.Step.WAITING_FOR_KPN);
                    sendMessage(chatId, "Введите КПН (€):");
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите корректное число для прибыли.");
                }
                break;
            case WAITING_FOR_KPN:
                try {
                    userState.setKpn(Double.parseDouble(input));
                    String result;
                    if (userState.isNewCompany()) {
                        // Добавляем данные в новую компанию
                        result = excelDataService.addDataToNewSheet(userState.getCompanyName(), userState.getMonth(),
                                userState.getIncome(), userState.getExpense(), userState.getProfit(), userState.getKpn());
                    } else {
                        // Добавляем данные в существующую компанию
                        result = excelDataService.addDataToExistingSheet(userState.getCompanyName(), userState.getMonth(),
                                userState.getIncome(), userState.getExpense(), userState.getProfit(), userState.getKpn());
                    }

                    sendMessage(chatId, result); // Сообщение о результате
                    userStates.remove(chatId); // Удаляем состояние после завершения

                    // Возвращаем пользователя в главное меню
                    showMainMenu(chatId);
                } catch (NumberFormatException e) {
                    sendMessage(chatId, "Пожалуйста, введите корректное число для КПН.");
                }
                break;
            case SELECT_EXISTING_COMPANY:
                userState.setCompanyName(input); // Устанавливаем выбранную компанию
                userState.setNewCompany(false); // Устанавливаем флаг существующей компании
                userState.setCurrentStep(UserState.Step.WAITING_FOR_MONTH);
                sendMessage(chatId, "Введите месяц:");
                break;
        }
    }

    private void showMainMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите действие:");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
        InlineKeyboardButton readButton = new InlineKeyboardButton();
        readButton.setText("Чтение данных");
        readButton.setCallbackData("read");
        rowInline1.add(readButton);

        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        InlineKeyboardButton addButton = new InlineKeyboardButton();
        addButton.setText("Добавление данных");
        addButton.setCallbackData("add");
        rowInline2.add(addButton);

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);
        keyboardMarkup.setKeyboard(rowsInline);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void showAddOptions(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите опцию добавления:");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<InlineKeyboardButton> rowInline1 = new ArrayList<>();
        InlineKeyboardButton existingButton = new InlineKeyboardButton();
        existingButton.setText("Добавить данные в существующую компанию");
        existingButton.setCallbackData("add_to_existing");
        rowInline1.add(existingButton);

        List<InlineKeyboardButton> rowInline2 = new ArrayList<>();
        InlineKeyboardButton newButton = new InlineKeyboardButton();
        newButton.setText("Создать новую компанию");
        newButton.setCallbackData("add_new_company");
        rowInline2.add(newButton);

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);
        keyboardMarkup.setKeyboard(rowsInline);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void startNewCompanyCreation(long chatId) {
        UserState userState = new UserState();
        userState.setCurrentStep(UserState.Step.WAITING_FOR_COMPANY_NAME);
        userStates.put(chatId, userState);

        sendMessage(chatId, "Введите название новой компании:");
    }

    private void startAddingDataToExistingCompany(long chatId, String sheetName) {
        UserState userState = new UserState();
        userState.setCompanyName(sheetName);
        userState.setCurrentStep(UserState.Step.WAITING_FOR_MONTH);
        userStates.put(chatId, userState);

        sendMessage(chatId, "Введите месяц:");
    }

    private void startAddingToExistingCompany(long chatId) {
        UserState userState = new UserState();
        userState.setCurrentStep(UserState.Step.SELECT_EXISTING_COMPANY);
        userStates.put(chatId, userState);

        showSheetSelection(chatId, "add_sheet_");
    }

    private void showSheetSelection(long chatId, String actionPrefix) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText("Выберите компанию для добавления данных:");

        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rowsInline = new ArrayList<>();

        List<String> sheets = excelDataService.getSheetNames();

        for (String sheet : sheets) {
            List<InlineKeyboardButton> rowInline = new ArrayList<>();
            InlineKeyboardButton button = new InlineKeyboardButton();
            button.setText(sheet);
            button.setCallbackData(actionPrefix + sheet);
            rowInline.add(button);
            rowsInline.add(rowInline);
        }

        keyboardMarkup.setKeyboard(rowsInline);
        message.setReplyMarkup(keyboardMarkup);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    // Метод для проверки обновлений данных каждые 60 секунд
    @Scheduled(fixedRate = 60000) // каждые 60 секунд
    public void checkForUpdates() {
        // Проверка обновлений для всех листов
        for (String sheetName : excelDataService.getSheetNames()) {
            String currentData = excelDataService.readExcelData(sheetName);

            // Проверяем, если текущие данные не совпадают с последними известными
            if (!currentData.equals(lastKnownData.get(sheetName))) {
                // Обновление обнаружено
                lastKnownData.put(sheetName, currentData); // Обновляем последнее известное состояние

                // Уведомляем всех пользователей об обновлении
                notifyUsersAboutUpdate(sheetName);
            }
        }
    }

    private void notifyUsersAboutUpdate(String sheetName) {
        String message = "Обновлены данные для компании: " + sheetName;
        for (Map.Entry<Long, UserState> entry : userStates.entrySet()) {
            Long chatId = entry.getKey();
            sendMessage(chatId, message);
        }
    }

    private void sendMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendFormattedMessage(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);
        message.setParseMode("HTML");

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
