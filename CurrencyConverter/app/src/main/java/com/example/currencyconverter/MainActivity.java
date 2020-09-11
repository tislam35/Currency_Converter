package com.example.currencyconverter;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.content.SharedPreferences;
import android.inputmethodservice.ExtractEditText;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Scroller;
import android.widget.Spinner;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity implements AdapterView.OnItemSelectedListener {

    //creating the array that will store the exchange rates
    //rates at (x,y) represents the exchange rate for going from x to y, i.e. x times rates(x,y) = y
    private static double[][] rates = new double[33][33];

    //int variables representing the current selection of currencies
    //the position of each currency type is hard-coded to allow easy location of rates
    //e.g. position 0 is Canadian dollars and position 1 is the honk kong dollar
    private static int startCurrenPos = 0;
    private static int endCurrenPos = 0;

    //variable to keep track of whether or not it is safe to change the input string into a double
    //this is only updated when the user finishes editing the input text field
    private boolean inpIsNum = false;

    //variable to let output field know if the rates being applied are newly fetched
    //or obtained from previously saved data
    private boolean oldRates = false;

    //method to turn rates into a string, for file storage
    private static String toStr(){
        String output ="";
        for(int i = 0; i < 33; i++){
            for(int j = 0; j < 33; j++){
                if(i == 32 && j == 32)
                    output += rates[i][j];
                else
                    output += rates[i][j] + ",";
            }
        }
        return output;
    }

    //method to turn a string into doubles and store into rates, for file retrieval
    private static void fillRates(String values){
        String[] valSep = values.split(",");
        int counter = 0;
        for(int i = 0; i < 33; i++){
            for(int j = 0; j < 33; j++){
                double r = Double.valueOf(valSep[counter]);
                rates[i][j] = r;
                counter ++;
            }
        }
    }

    //checking to see if rates were retrieved inorder for the user to use the app
    //handles "internet" checking and data storage/retrieval
    private boolean canUseTheApp(){
        SharedPreferences sharedPreferences = getPreferences(MODE_PRIVATE);
        //rate retrieval was unsuccessful
        if(rates[0][0] == 0) {
            String savedData = sharedPreferences.getString("rates", null);
            //no saved files exist, user cant use the app
            if (savedData == null) {
                EditText output = findViewById(R.id.editText5);
                output.setText("There is currently no saved data or internet available. Please connect to the internet to use the App");
                return false;
            }
            //a file of old rates exist, app will use that to give the user a rough estimate of what their money is worth
            else{
                //loading file into rates array
                fillRates(savedData);
                oldRates = true;
                return true;
            }
        }
        //rate retrieval was successful
        else{
            //saving the current data in sharedpreferences
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putString("rates", toStr());
            editor.apply();
            return true;
        }
    }


    //method to update the value of the output text field
    //gets called when the user interacts with a dropdown menu or input field
    //will only be used if canUseTheApp returns true
    private void updateOutput(){
        EditText output = findViewById(R.id.editText5);
        if(inpIsNum) {
            //turning the input into a double
            EditText input = findViewById(R.id.editText4);
            String temp = input.getText().toString();
            double inpVal = Double.valueOf(temp);
            //calculating converted value
            double ans = rates[startCurrenPos][endCurrenPos] * inpVal;
            //updating the output text field
            output.setText(String.format("Your converted amount is:%n%,.2f", ans));
            if(oldRates)
                output.append("\nWarning: No internet, Using old rates");
        }
        else
            //we clear output since value entered is invalid
            output.setText("Your converted amount is:");
    }

    //creating a focusChangeListener object and overriding the method
    //this is to check user input and update the output field when user taps away from the input box
    private View.OnFocusChangeListener clickAway = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            //checking if input is valid and if it is, calculating a value for output with updateOutput
            if(!hasFocus) {
                if(canUseTheApp()) {
                    EditText input = findViewById(R.id.editText4);
                    //whole number
                    boolean isValidWholeNum = input.getText().toString().matches("\\d+.{0,1}");
                    //has decimals
                    boolean isValidDecimal = input.getText().toString().matches("\\d*.\\d\\d");
                    if (isValidWholeNum || isValidDecimal) {
                        inpIsNum = true;
                        updateOutput();
                    } else {
                        input.setText("Invalid amount");
                        inpIsNum = false;
                        updateOutput();
                    }
                }
            }
        }
    };

    //overridden methods for the event listener for the drop down menus
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        switch (parent.getId()){
            case R.id.spinner:
                //user has changed the first dropdown menu
                startCurrenPos = position;
                if(inpIsNum) {
                    if(canUseTheApp()) {
                        updateOutput();
                    }
                }
                break;
            case R.id.spinner2:
                //user has changed the second dropdown menu
                endCurrenPos = position;
                if(inpIsNum) {
                    if(canUseTheApp()) {
                        updateOutput();
                    }
                }
                break;
        }
    }

    //if nothing selected do nothing
    @Override
    public void onNothingSelected(AdapterView<?> parent) {}

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //running the background process to obtain latest currency exchange rates
        new fetchData().execute();

        //declaring the variables/widgets that will have event listeners
        EditText input = findViewById(R.id.editText4);
        Spinner spnr1 = findViewById(R.id.spinner);
        Spinner spnr2 = findViewById(R.id.spinner2);

        //checking if the user has finished entering a value in the input field
        input.setOnFocusChangeListener(clickAway);

        //setting up the dropdown menu for currency choices
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.currencies, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spnr1.setAdapter(adapter);
        spnr2.setAdapter(adapter);
        spnr1.setOnItemSelectedListener(this);
        spnr2.setOnItemSelectedListener(this);
    }

    //class to run in another thread and fetch the currency rates
    public class fetchData extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            //creating an array of all the currency types we consider
            String[] types = new String[]{"CAD", "HKD", "ISK", "PHP", "DKK", "HUF", "CZK", "GBP", "RON",
            "SEK", "IDR", "INR", "BRL", "RUB", "HRK", "JPY", "THB", "CHF", "EUR", "MYR", "BGN", "TRY", "CNY",
            "NOK", "NZD", "ZAR", "USD", "MXN", "SGD", "AUD", "ILS", "KRW", "PLN"};
            //obtaining the rates for each type of currency
            for(int i = 0; i < types.length; i++) {
                try {
                    URL url = new URL("https://api.exchangeratesapi.io/latest?base=" + types[i]);
                    HttpsURLConnection urlConnection = (HttpsURLConnection)url.openConnection();
                    try {
                        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()));
                        //variable line will have all the rates in a single line, need to parse the data
                        String line = bufferedReader.readLine();
                        bufferedReader.close();
                        //removing the excess information at the beginning and end of line
                        line = line.substring(line.indexOf(':') + 2, line.indexOf("\"base") - 2);
                        //separating each currency type
                        String[] separatedRates = line.split(",");
                        //getting the double value for each type and adding its value to rates variable
                        for(int j = 0; j < separatedRates.length; j++){
                            int separator = separatedRates[j].indexOf(':') + 1;
                            String singleRate = separatedRates[j].substring(separator);
                            double valueAtPosJ = Double.valueOf(singleRate);
                            rates[i][j] = valueAtPosJ;
                        }
                        oldRates = false;
                    } finally {
                        urlConnection.disconnect();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
        }
    }
}