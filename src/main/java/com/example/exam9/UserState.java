package com.example.exam9;

public class UserState {

    public enum Step {
        WAITING_FOR_COMPANY_NAME,
        WAITING_FOR_MONTH,
        WAITING_FOR_INCOME,
        WAITING_FOR_EXPENSE,
        WAITING_FOR_PROFIT,
        WAITING_FOR_KPN,
        SELECT_EXISTING_COMPANY
    }

    private Step currentStep;
    private String companyName;
    private String month;
    private double income;
    private double expense;
    private double profit;
    private double kpn;
    private boolean isNewCompany;

    public Step getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(Step currentStep) {
        this.currentStep = currentStep;
    }

    public String getCompanyName() {
        return companyName;
    }

    public void setCompanyName(String companyName) {
        this.companyName = companyName;
    }

    public String getMonth() {
        return month;
    }

    public void setMonth(String month) {
        this.month = month;
    }

    public double getIncome() {
        return income;
    }

    public void setIncome(double income) {
        this.income = income;
    }

    public double getExpense() {
        return expense;
    }

    public void setExpense(double expense) {
        this.expense = expense;
    }

    public double getProfit() {
        return profit;
    }

    public void setProfit(double profit) {
        this.profit = profit;
    }

    public double getKpn() {
        return kpn;
    }

    public void setKpn(double kpn) {
        this.kpn = kpn;
    }

    public boolean isNewCompany() {
        return isNewCompany;
    }

    public void setNewCompany(boolean newCompany) {
        isNewCompany = newCompany;
    }
}
