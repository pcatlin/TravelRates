package com.ecatlin.travelrates;

import android.app.AlertDialog;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.DialogInterface;
import android.content.Loader;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.MobileAds;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DecimalFormat;

public class MainActivity extends AppCompatActivity
        implements LoaderCallbacks<CurrencyRates>{

    private AdView mAdView;
    private Currency chosenCurrency;
    private String homeCurrency = "GBP";
    private String prefSelectedCurrency;
    private String prefCustomRate;
    private String prefLocation;
    //private ArrayList<Country> Countries = new ArrayList<>();
    //private Country userNetworkCountry, userSIMCountry;
    private Cache userPrefs, ratesCache;
    SpinnerAdapter spinnerAdapter;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()) {
            case R.id.setCustom:
                showEditCustomRateDialog();
                return true;
            case R.id.setHome:
                showSetHomeDialog(spinnerAdapter.rates.getAllCurrencies());
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ratesCache = new Cache("rates");
        userPrefs = new Cache("prefs");

        Boolean prefsExist=readPrefs();

        //populateCountries();
        //inHomeCountry(this);

        CurrencyRates rates;
        rates = getCurrencyRates();

        // if prefs do not exist (first run of app?) show set home currency dialog box
        if(!prefsExist) showSetHomeDialog(rates.getAllCurrencies());

        // remember last used currency from prefs
        chosenCurrency=rates.findCurrencyFromCode(prefSelectedCurrency);

        // TODO move spinner loading to function
        // populate the spinner/dropdown box with currencies
        final Spinner spinner = (Spinner) findViewById(R.id.convertTo);
        spinnerAdapter = new SpinnerAdapter(this, R.layout.spinner_row, R.id.currencycode, rates);

        // Specify the layout to use when the list of choices appears
        spinner.setAdapter(spinnerAdapter);
        spinner.setSelection(spinnerAdapter.findCodePosition(prefSelectedCurrency));
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

                chosenCurrency=spinnerAdapter.getCurrency(position);

                if(chosenCurrency.getCurrencyCode().equals(getString(R.string.customRateCode)) && chosenCurrency.getRate()==1.0){
                    // custom rate chosen
                    showEditCustomRateDialog();
                }

                TextView rate = (TextView)findViewById(R.id.rateText);
                rate.setText(getString(R.string.rate, chosenCurrency.getStringRate()));

                //Log.d("SPINNER", "ChosenCurrency->getcode:" + chosenCurrency.getCurrencyCode() + " chosenCurrency->getRate:" + chosenCurrency.getRate() + "\n" + "ChosenRateIndex: " + chosenRateIndex);

                savePrefs();
                updateNumbers();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });



        TextView rate = (TextView)findViewById(R.id.rateText);
        rate.setText(getString(R.string.rate, chosenCurrency.getStringRate()));

        final EditText editCustomHome = (EditText) findViewById(R.id.editCustomHome);
        final EditText editCustomAway = (EditText) findViewById(R.id.editCustomAway);
        final TextView customAway = (TextView) findViewById(R.id.customAway);
        final TextView customHome = (TextView) findViewById(R.id.customhome);
        final DecimalFormat precision = new DecimalFormat("0.00");

        editCustomHome.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length()>0) {
                    double conversion = Double.parseDouble(s.toString());
                    conversion *= chosenCurrency.getRate();
                    customAway.setText(getString(R.string.currencyvalue, precision.format(conversion), chosenCurrency.getCurrencyCode()));
                }else customAway.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {  }
        });

        editCustomAway.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length()>0) {
                    double conversion = Double.parseDouble(s.toString());
                    conversion /= chosenCurrency.getRate();
                    customHome.setText(getString(R.string.currencyvalue, precision.format(conversion), homeCurrency));
                }else customHome.setText("");
            }

            @Override
            public void afterTextChanged(Editable s) {  }
        });

        updateNumbers();

        mAdView = (AdView)findViewById(R.id.adView);
        View mDivider = findViewById(R.id.dividerline);

        if (BuildConfig.FLAVOR.equals("ads")) {

            MobileAds.initialize(this, "ca-app-pub-9612116433207542~2023116000");

            AdRequest adRequest = new AdRequest.Builder()
                    .addTestDevice(AdRequest.DEVICE_ID_EMULATOR)
                    .addTestDevice("B016E2BD0ED7D45E64EF6901DAA24B31")
                    .build();
            mAdView.loadAd(adRequest);

        }else{
            mAdView.setVisibility(View.GONE);
            mDivider.setVisibility(View.GONE);
        }

    }

    @Override
    public Loader<CurrencyRates> onCreateLoader(int i, Bundle bundle) {
        // Create a new loader for the given URL
        return new CurrencyLoader(this, getString(R.string.url) + homeCurrency);
    }

    @Override
    public void onLoadFinished(Loader<CurrencyRates> loader, CurrencyRates downloadedRates) {

        //Log.d("onLoadFinished","Data downloaded");
        if (downloadedRates.getDateUpdated() != null) {

            downloadedRates.Add(new Currency("?",Double.valueOf(prefCustomRate)));
            spinnerAdapter.updateRates(downloadedRates.mCurrencies);

            ratesCache.write(this, downloadedRates.getJSON());

            TextView date = (TextView) findViewById(R.id.updatedText);
            date.setText(downloadedRates.getDateUpdated());
        }
    }

    @Override
    public void onLoaderReset(Loader<CurrencyRates> loader) {
        // Loader reset, so we can clear out our existing data.
        //spinnerAdapter.rates.Empty();
        ////// hmmmmmmmmm
    }


    private CurrencyRates getCurrencyRates(){

        String tempJSON = "{\"base\":\"GBP\",\"date\":\"2017-03-10\",\"rates\":{\"AUD\":1.6155,\"BGN\":2.2416,\"BRL\":3.8618,\"CAD\":1.6415,\"CHF\":1.2313,\"CNY\":8.4053,\"CZK\":30.97,\"DKK\":8.5193,\"EUR\":1.1461,\"HKD\":9.4403,\"HRK\":8.5032,\"HUF\":357.9,\"IDR\":16259.0,\"ILS\":4.4798,\"INR\":80.999,\"JPY\":140.31,\"KRW\":1405.5,\"MXN\":24.032,\"MYR\":5.4133,\"NOK\":10.476,\"NZD\":1.7591,\"PHP\":61.067,\"PLN\":4.9581,\"RON\":5.2138,\"RUB\":71.858,\"SEK\":10.977,\"SGD\":1.7239,\"THB\":43.044,\"TRY\":4.5617,\"USD\":1.2156,\"ZAR\":16.124}}";

        // read cache file
        String rates = ratesCache.read(this);

        if(rates.equals("")){
            // no file
            // use old hardcoded data for now
            rates = tempJSON;

            // and start loading new data from the web
            getLoaderManager().initLoader(0, null, this);

            //Log.d("getRates","No cache file found. Using ancient rates and getting new rates from net.");

        }else if(ratesCache.cacheOld(this)) {

            // start loading new data from the web
            getLoaderManager().initLoader(0, null, this);
            //Log.d("getRates","Old cache file found. Age: " + fileAge + " Using its rates and getting new rates from net.");

        }else {
            //Log.d("getRates","Recent cache file found.");
        }

        CurrencyRates cr = new CurrencyRates();
        cr.parseJSONrates(rates);

        if(!homeCurrency.equals(cr.getBase())){
            // if the home country has changed, retrieve new rates from the net
            //getLoaderManager().initLoader(0, null, this);
            getLoaderManager().restartLoader(0,null,this);
        }

        cr.Add(new Currency(getString(R.string.customRateCode), Double.valueOf(prefCustomRate)));  // custom rate

        TextView date = (TextView) findViewById(R.id.updatedText);
        date.setText(cr.getDateUpdated());

        return cr;
    }

    private Boolean readPrefs(){
        String defaultPrefs = "{\"selectedCurrency\":\"EUR\",\"customRate\":1,\"homeCurrency\":\"GBP\",\"location\":\"home\"}";
        Boolean existing = true;

        String prefs = userPrefs.read(this);
        if(prefs.equals("")){
            // no file, use defaults
            prefs = defaultPrefs;
            existing = false;
        }

        try {

            JSONObject root =  new JSONObject(prefs);
            prefSelectedCurrency = root.getString("selectedCurrency");
            prefCustomRate = root.getString("customRate");
            homeCurrency = root.getString("homeCurrency");
            prefLocation = root.getString("location");

            //Log.d("readPrefs", "Parsed JSON dated: ");

        } catch (JSONException e) {
            Log.e("readPrefs", "Problem parsing the JSON userPrefs", e);
        }

        return existing;

    }

    private void savePrefs(){

        try {
            JSONObject jsonObj = new JSONObject();
            jsonObj.put("selectedCurrency", chosenCurrency.getCurrencyCode());
            jsonObj.put("customRate", spinnerAdapter.rates.getCustomRateString());
            jsonObj.put("homeCurrency", homeCurrency);
            jsonObj.put("location", "home"); // not used yet...

            userPrefs.write(this, jsonObj.toString());
            //Log.d("savePrefs", "Prefs saved");
        }
        catch(JSONException ex) {
            ex.printStackTrace();
            Log.e("savePrefs", "Problem saving prefs");
        }

    }


    private void updateNumbers() {

        EditText CustomHomeEdit = (EditText) findViewById(R.id.editCustomHome);
        CustomHomeEdit.setHint(getString(R.string.custom, homeCurrency));
        CustomHomeEdit.setText("");

        EditText CustomAwayEdit = (EditText) findViewById(R.id.editCustomAway);
        CustomAwayEdit.setHint(getString(R.string.custom, chosenCurrency.getCurrencyCode()));
        CustomAwayEdit.setText("");

        DecimalFormat precision;
        if(chosenCurrency.getRate()<10) precision = new DecimalFormat("0.00");
        else precision = new DecimalFormat("0");

        TextView home1 = (TextView) findViewById(R.id.home1);
        TextView home2 = (TextView) findViewById(R.id.home2);
        TextView home3 = (TextView) findViewById(R.id.home3);
        TextView home4 = (TextView) findViewById(R.id.home4);
        TextView home5 = (TextView) findViewById(R.id.home5);
        TextView home11 = (TextView) findViewById(R.id.home11);
        TextView home12 = (TextView) findViewById(R.id.home12);
        TextView home13 = (TextView) findViewById(R.id.home13);
        TextView home14 = (TextView) findViewById(R.id.home14);
        TextView home15 = (TextView) findViewById(R.id.home15);

        TextView away1 = (TextView) findViewById(R.id.away1);
        TextView away2 = (TextView) findViewById(R.id.away2);
        TextView away3 = (TextView) findViewById(R.id.away3);
        TextView away4 = (TextView) findViewById(R.id.away4);
        TextView away5 = (TextView) findViewById(R.id.away5);
        TextView away11 = (TextView) findViewById(R.id.away11);
        TextView away12 = (TextView) findViewById(R.id.away12);
        TextView away13 = (TextView) findViewById(R.id.away13);
        TextView away14 = (TextView) findViewById(R.id.away14);
        TextView away15 = (TextView) findViewById(R.id.away15);

        home1.setTag(1);
        home2.setTag(5);
        home3.setTag(20);
        home4.setTag(35);
        home5.setTag(50);

        if(chosenCurrency.getRate()<3) {
            away11.setTag(1);
            away12.setTag(5);
            away13.setTag(20);
            away14.setTag(35);
            away15.setTag(50);
        }else if(chosenCurrency.getRate()<6) {
            away11.setTag(5);
            away12.setTag(25);
            away13.setTag(100);
            away14.setTag(150);
            away15.setTag(250);
        }else if(chosenCurrency.getRate()<9) {
            away11.setTag(10);
            away12.setTag(50);
            away13.setTag(150);
            away14.setTag(300);
            away15.setTag(500);
        }else if(chosenCurrency.getRate()<13) {
            away11.setTag(10);
            away12.setTag(75);
            away13.setTag(250);
            away14.setTag(400);
            away15.setTag(1000);
        }else if(chosenCurrency.getRate()<30) {
            away11.setTag(30);
            away12.setTag(200);
            away13.setTag(500);
            away14.setTag(750);
            away15.setTag(1500);
        }else if(chosenCurrency.getRate()<50) {
            away11.setTag(50);
            away12.setTag(500);
            away13.setTag(1000);
            away14.setTag(2000);
            away15.setTag(5000);
        }else if(chosenCurrency.getRate()<100) {
            away11.setTag(75);
            away12.setTag(1000);
            away13.setTag(2000);
            away14.setTag(4000);
            away15.setTag(10000);
        }else if(chosenCurrency.getRate()<160) {
            away11.setTag(100);
            away12.setTag(1500);
            away13.setTag(3000);
            away14.setTag(5000);
            away15.setTag(10000);
        }else if(chosenCurrency.getRate()<350) {
            away11.setTag(300);
            away12.setTag(1000);
            away13.setTag(5000);
            away14.setTag(10000);
            away15.setTag(25000);
        }else if(chosenCurrency.getRate()<1600) {
            away11.setTag(1000);
            away12.setTag(5000);
            away13.setTag(20000);
            away14.setTag(50000);
            away15.setTag(100000);
        }else{
            away11.setTag(20000);
            away12.setTag(100000);
            away13.setTag(300000);
            away14.setTag(700000);
            away15.setTag(1000000);
        }

        home1.setText(getString(R.string.currencyvalue, home1.getTag(), homeCurrency));
        home2.setText(getString(R.string.currencyvalue, home2.getTag(), homeCurrency));
        home3.setText(getString(R.string.currencyvalue, home3.getTag(), homeCurrency));
        home4.setText(getString(R.string.currencyvalue, home4.getTag(), homeCurrency));
        home5.setText(getString(R.string.currencyvalue, home5.getTag(), homeCurrency));
        home11.setText(getString(R.string.currencyvalue, precision.format((int)away11.getTag() / chosenCurrency.getRate()), homeCurrency));
        home12.setText(getString(R.string.currencyvalue, precision.format((int)away12.getTag() / chosenCurrency.getRate()), homeCurrency));
        home13.setText(getString(R.string.currencyvalue, precision.format((int)away13.getTag() / chosenCurrency.getRate()), homeCurrency));
        home14.setText(getString(R.string.currencyvalue, precision.format((int)away14.getTag() / chosenCurrency.getRate()), homeCurrency));
        home15.setText(getString(R.string.currencyvalue, precision.format((int)away15.getTag() / chosenCurrency.getRate()), homeCurrency));

        away1.setText(getString(R.string.currencyvalue, precision.format((int)home1.getTag() * chosenCurrency.getRate()), chosenCurrency.getCurrencyCode()));
        away2.setText(getString(R.string.currencyvalue, precision.format((int)home2.getTag() * chosenCurrency.getRate()), chosenCurrency.getCurrencyCode()));
        away3.setText(getString(R.string.currencyvalue, precision.format((int)home3.getTag() * chosenCurrency.getRate()), chosenCurrency.getCurrencyCode()));
        away4.setText(getString(R.string.currencyvalue, precision.format((int)home4.getTag() * chosenCurrency.getRate()), chosenCurrency.getCurrencyCode()));
        away5.setText(getString(R.string.currencyvalue, precision.format((int)home5.getTag() * chosenCurrency.getRate()), chosenCurrency.getCurrencyCode()));
        away11.setText(getString(R.string.currencyvalue, away11.getTag(), chosenCurrency.getCurrencyCode()));
        away12.setText(getString(R.string.currencyvalue, away12.getTag(), chosenCurrency.getCurrencyCode()));
        away13.setText(getString(R.string.currencyvalue, away13.getTag(), chosenCurrency.getCurrencyCode()));
        away14.setText(getString(R.string.currencyvalue, away14.getTag(), chosenCurrency.getCurrencyCode()));
        away15.setText(getString(R.string.currencyvalue, away15.getTag(), chosenCurrency.getCurrencyCode()));
    }

    public void showSetHomeDialog(CurrencyRates currencies){

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.sethome_dialog, null);
        dialogBuilder.setView(dialogView);


        // TODO populate spinner with all countries
        final Spinner sp = (Spinner) dialogView.findViewById(R.id.setHomeSpinner);
        final SpinnerAdapter spinnerAdapter2 = new SpinnerAdapter(this, R.layout.spinner_row, R.id.currencycode, currencies);

        // Specify the layout to use when the list of choices appears
        sp.setAdapter(spinnerAdapter2);
        sp.setSelection(spinnerAdapter2.findCodePosition(homeCurrency));
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        dialogBuilder.setTitle(R.string.setHome);
        dialogBuilder.setMessage(R.string.setHomeDesc);
        dialogBuilder.setPositiveButton(getString(R.string.set), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                Currency selectedCurrency = (Currency)sp.getSelectedItem();
                homeCurrency = selectedCurrency.getCurrencyCode();
                savePrefs();
                CurrencyRates rates;
                rates = getCurrencyRates();
                spinnerAdapter.updateRates(rates.mCurrencies);
                updateNumbers();
            }
        });

        dialogBuilder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //pass
            }
        });
        AlertDialog b = dialogBuilder.create();
        b.show();
    }

    public void showEditCustomRateDialog(){

        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
        LayoutInflater inflater = this.getLayoutInflater();
        final View dialogView = inflater.inflate(R.layout.customrate_dialog, null);
        dialogBuilder.setView(dialogView);


        final EditText edt = (EditText) dialogView.findViewById(R.id.UserCustomRate);
        String previousCustom = spinnerAdapter.rates.getCustomRateString();
        if(!previousCustom.equals("1")){
            int length = previousCustom.length();
            edt.setText(previousCustom);
            edt.setSelection(length);
        }

        dialogBuilder.setTitle(R.string.setCustomRate);
        dialogBuilder.setMessage(R.string.customRateDesc);
        dialogBuilder.setPositiveButton(getString(R.string.done), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String input = edt.getText().toString();
                if(input.equals("")) input="1"; // if blank, reset to 1
                setCustomRate(Double.parseDouble(input));
            }
        });

        dialogBuilder.setOnKeyListener(new DialogInterface.OnKeyListener() {

            @Override
            public boolean onKey(DialogInterface dialog, int keyCode, KeyEvent event) {
                if(keyCode==KeyEvent.KEYCODE_ENTER) {

                    String input = edt.getText().toString();
                    if(input.equals("")) input="1"; // if blank, reset to 1
                    setCustomRate(Double.parseDouble(input));

                    dialog.dismiss();
                    return true;
                }
                return false;
            }
        });

        dialogBuilder.setNegativeButton(getString(R.string.cancel), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                //pass

            }
        });
        AlertDialog b = dialogBuilder.create();
        b.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        b.show();
    }


    void setCustomRate(double CustomRate){

        // set spinner index to be custom rate
        Spinner spinner = (Spinner) findViewById(R.id.convertTo);
        spinnerAdapter.rates.setCustomRate(CustomRate);
        spinnerAdapter.notifyDataSetChanged();

        int lastItemIndex = spinnerAdapter.getCount()-1;
        spinner.setSelection(lastItemIndex);

        chosenCurrency=spinnerAdapter.getCurrency(lastItemIndex);

        savePrefs();
        updateNumbers();

        TextView rate = (TextView)findViewById(R.id.rateText);
        rate.setText(getString(R.string.rate, chosenCurrency.getStringRate()));

    }

    /*
    private void populateCountries(){
        String csv = getString(R.string.countryMappingCSV);
        String[] line = csv.split("\n");
        for(int i=1; i<line.length; i++){  // skip headers row[0]
            String[] RowData = line[i].split(",");
            Countries.add(new Country(RowData[0],RowData[1],RowData[2],RowData[3]));
        }
    }
    */


    /*
      Get ISO 3166-1 alpha-2 country code for this device (or null if not available)
      @param context Context reference to get the TelephonyManager instance from
     */
    /*
    private Boolean inHomeCountry(Context context) {
        try {

            final TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String simCountry = tm.getSimCountryIso();

            if (simCountry != null && simCountry.length() == 2) { // SIM country code is available

                simCountry = simCountry.toUpperCase(Locale.UK);
                userSIMCountry = new Country(simCountry);
                Log.d("PLACE", "SIM country: " + simCountry);

            }

            if (tm.getPhoneType() != TelephonyManager.PHONE_TYPE_CDMA) { // device is not 3G (would be unreliable)

                String networkCountry = tm.getNetworkCountryIso();
                if (networkCountry != null && networkCountry.length() == 2) { // network country code is available

                    networkCountry = networkCountry.toUpperCase(Locale.UK);
                    userNetworkCountry = new Country(networkCountry);
                    Log.d("PLACE", "Network country: " + networkCountry);

                    return (userSIMCountry==userNetworkCountry);

                }
            }
        }
        catch (Exception e) { }
        return null;

    }
    */

}