
package ro.fotbalpredictor.ai;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import android.text.InputType;
import org.json.*;
import java.net.*;
import java.io.*;

public class MainActivity extends Activity {

    LinearLayout root, list;
    EditText apiKey;
    String API = "https://v3.football.api-sports.io/";

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(10,10,10));
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(20,20,20,20);
        root.setBackgroundColor(Color.rgb(12,15,18));

        TextView title = new TextView(this);
        title.setText("⚽ FOTBAL PREDICTOR AI");
        title.setTextColor(Color.GREEN);
        title.setTextSize(25);
        title.setGravity(17);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Analiză meciuri • Scor corect • 1X2");
        subtitle.setTextColor(Color.WHITE);
        subtitle.setGravity(17);
        root.addView(subtitle);

        apiKey = new EditText(this);
        apiKey.setHint("Introdu cheia API-Football");
        apiKey.setHintTextColor(Color.GRAY);
        apiKey.setTextColor(Color.WHITE);
        apiKey.setSingleLine(true);
        root.addView(apiKey);

        Button load = new Button(this);
        load.setText("ÎNCARCĂ MECIURILE");
        root.addView(load);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(1);
        scroll.addView(list);
        root.addView(scroll);

        setContentView(root);

        load.setOnClickListener(v -> loadMatches());
    }

    void loadMatches() {
        String key = apiKey.getText().toString().trim();
        if(key.isEmpty()) {
            Toast.makeText(this,"Introdu cheia API!",Toast.LENGTH_LONG).show();
            return;
        }

        list.removeAllViews();
        TextView loading = new TextView(this);
        loading.setText("Se caută meciurile...");
        loading.setTextColor(Color.WHITE);
        list.addView(loading);

        new Thread(() -> {
            try {
                String date = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd", java.util.Locale.US
                ).format(new java.util.Date());

                URL url = new URL(API + "fixtures?date=" + date +
                    "&timezone=Europe%2FBucharest");

                HttpURLConnection c = (HttpURLConnection)url.openConnection();
                c.setRequestProperty("x-apisports-key",key);
                c.setConnectTimeout(15000);
                c.setReadTimeout(15000);

                BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream())
                );
                StringBuilder result = new StringBuilder();
                String line;
                while((line=r.readLine())!=null) result.append(line);
                c.disconnect();

                JSONObject data = new JSONObject(result.toString());
                JSONArray matches = data.getJSONArray("response");

                runOnUiThread(() -> {
                    list.removeAllViews();
                    if(matches.length()==0) {
                        addText("Nu sunt meciuri găsite astăzi.");
                    }
                    for(int i=0;i<matches.length();i++) {
                        JSONObject m = matches.optJSONObject(i);
                        JSONObject teams = m.optJSONObject("teams");
                        JSONObject home = teams.optJSONObject("home");
                        JSONObject away = teams.optJSONObject("away");

                        String h = home.optString("name");
                        String a = away.optString("name");
                        int id = m.optJSONObject("fixture").optInt("id");

                        Button match = new Button(this);
                        match.setText(h + "  -  " + a);
                        match.setTextColor(Color.WHITE);
                        match.setBackgroundTintList(
                            android.content.res.ColorStateList.valueOf(
                                Color.rgb(30,90,45)
                            )
                        );
                        list.addView(match);

                        match.setOnClickListener(v ->
                            showPrediction(h,a,id)
                        );
                    }
                });

            } catch(Exception e) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    addText("Eroare API: " + e.getMessage());
                });
            }
        }).start();
    }

    void showPrediction(String home,String away,int id) {
        list.removeAllViews();
        addText(home + " - " + away);
        addText("Analiza statistică urmează să fie adăugată.");
        addText("Nu există pariuri sigure. Predicțiile sunt estimări.");
    }

    void addText(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setPadding(10,15,10,15);
        list.addView(t);
    }
}
