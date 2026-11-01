
package ro.fotbalpredictor.ai;

import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;
import android.content.res.ColorStateList;
import org.json.*;
import java.net.*;
import java.io.*;
import java.util.*;

public class MainActivity extends Activity {

    LinearLayout root, list;
    EditText apiKey;
    String API = "https://v3.football.api-sports.io/";

    public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        root = new LinearLayout(this);
        root.setOrientation(1);
        root.setPadding(16,16,16,16);
        root.setBackgroundColor(Color.rgb(12,15,18));

        TextView title = new TextView(this);
        title.setText("⚽ FOTBAL PREDICTOR AI");
        title.setTextColor(Color.GREEN);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Analiză automată • 1X2 • Goluri • Scor corect");
        sub.setTextColor(Color.WHITE);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);

        apiKey = new EditText(this);
        apiKey.setHint("Cheia API-Football");
        apiKey.setSingleLine(true);
        apiKey.setTextColor(Color.WHITE);
        apiKey.setHintTextColor(Color.GRAY);
        root.addView(apiKey);

        Button load = new Button(this);
        load.setText("ÎNCARCĂ MECIURILE");
        root.addView(load);

        ScrollView scroll = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(1);
        scroll.addView(list);
        root.addView(scroll, new LinearLayout.LayoutParams(
            -1, 0, 1
        ));

        setContentView(root);
        load.setOnClickListener(v -> loadMatches());
    }

    JSONObject request(String path, String key) throws Exception {
        URL url = new URL(API + path);
        HttpURLConnection c = (HttpURLConnection)url.openConnection();
        c.setRequestProperty("x-apisports-key", key);
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);

        int status = c.getResponseCode();
        InputStream stream = status >= 400 ?
            c.getErrorStream() : c.getInputStream();

        BufferedReader r = new BufferedReader(
            new InputStreamReader(stream)
        );
        StringBuilder out = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) out.append(line);
        r.close();
        c.disconnect();

        JSONObject result = new JSONObject(out.toString());
        if (status >= 400)
            throw new Exception("API HTTP " + status);
        return result;
    }

    void loadMatches() {
        String key = apiKey.getText().toString().trim();
        if (key.isEmpty()) {
            Toast.makeText(this,"Introdu cheia API!",Toast.LENGTH_LONG).show();
            return;
        }

        list.removeAllViews();
        addText("Se caută meciurile...");

        new Thread(() -> {
            try {
                String date = new java.text.SimpleDateFormat(
                    "yyyy-MM-dd", Locale.US
                ).format(new Date());

                JSONObject data = request(
                    "fixtures?date=" + date +
                    "&timezone=Europe%2FBucharest", key
                );

                JSONArray matches = data.getJSONArray("response");

                runOnUiThread(() -> {
                    list.removeAllViews();

                    if (matches.length() == 0)
                        addText("Nu sunt meciuri programate astăzi.");

                    for (int i=0; i<matches.length(); i++) {
                        JSONObject m = matches.optJSONObject(i);
                        JSONObject teams = m.optJSONObject("teams");
                        JSONObject home = teams.optJSONObject("home");
                        JSONObject away = teams.optJSONObject("away");

                        String h = home.optString("name");
                        String a = away.optString("name");
                        int homeId = home.optInt("id");
                        int awayId = away.optInt("id");

                        Button match = new Button(this);
                        match.setText(h + "  -  " + a);
                        match.setTextColor(Color.WHITE);
                        match.setBackgroundTintList(
                            ColorStateList.valueOf(Color.rgb(25,90,45))
                        );
                        list.addView(match);

                        match.setOnClickListener(v ->
                            analyze(h,a,homeId,awayId,key)
                        );
                    }
                });

            } catch(Exception e) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    addText("Eroare: " + e.getMessage());
                });
            }
        }).start();
    }

    double[] teamStats(int teamId, String key) throws Exception {
        JSONObject data = request(
            "fixtures?team=" + teamId + "&last=10", key
        );
        JSONArray games = data.getJSONArray("response");

        double scored = 0, conceded = 0;
        int count = 0;

        for (int i=0; i<games.length(); i++) {
            JSONObject game = games.getJSONObject(i);
            JSONObject teams = game.getJSONObject("teams");
            JSONObject goals = game.getJSONObject("goals");

            int homeId = teams.getJSONObject("home").getInt("id");
            int homeGoals = goals.optInt("home", -1);
            int awayGoals = goals.optInt("away", -1);

            if (homeGoals < 0 || awayGoals < 0) continue;

            if (homeId == teamId) {
                scored += homeGoals;
                conceded += awayGoals;
            } else {
                scored += awayGoals;
                conceded += homeGoals;
            }
            count++;
        }

        if (count == 0) throw new Exception(
            "Nu există suficiente rezultate pentru echipa selectată."
        );

        return new double[] {scored/count, conceded/count};
    }

    double poisson(int goals, double lambda) {
        double p = Math.exp(-lambda);
        for (int i=1; i<=goals; i++)
            p *= lambda/i;
        return p;
    }

    void analyze(String home, String away,
                 int homeId, int awayId, String key) {

        list.removeAllViews();
        addText("Analizăm ultimele 10 meciuri...");
        addText(home + " - " + away);

        new Thread(() -> {
            try {
                double[] hs = teamStats(homeId,key);
                double[] as = teamStats(awayId,key);

                double homeLambda = Math.max(0.15,
                    (hs[0] + as[1]) / 2.0 * 1.08);
                double awayLambda = Math.max(0.15,
                    (as[0] + hs[1]) / 2.0 * 0.92);

                double[] scores = new double[3];
                double over = 0, under = 0;
                double[][] matrix = new double[6][6];

                for(int h=0; h<=5; h++) {
                    for(int a=0; a<=5; a++) {
                        double p = poisson(h,homeLambda) *
                                   poisson(a,awayLambda);
                        matrix[h][a] = p;

                        if(h>a) scores[0] += p;
                        else if(h==a) scores[1] += p;
                        else scores[2] += p;

                        if(h+a>=3) over += p;
                        else under += p;
                    }
                }

                double total = scores[0]+scores[1]+scores[2]+over*0;
                double[] top = new double[10];
                String[] names = new String[10];

                for(int h=0; h<=5; h++) {
                    for(int a=0; a<=5; a++) {
                        double p = matrix[h][a];
                        for(int j=0;j<10;j++) {
                            if(p>top[j]) {
                                for(int k=9;k>j;k--) {
                                    top[k]=top[k-1];
                                    names[k]=names[k-1];
                                }
                                top[j]=p;
                                names[j]=h+" - "+a;
                                break;
                            }
                        }
                    }
                }

                double finalTotal = total;
                runOnUiThread(() -> {
                    list.removeAllViews();
                    addText("⚽ " + home + " - " + away);
                    addText("PREDICȚIE 1X2");
                    addText("1 - Gazde: " + percent(scores[0]/finalTotal));
                    addText("X - Egal: " + percent(scores[1]/finalTotal));
                    addText("2 - Oaspeți: " + percent(scores[2]/finalTotal));

                    addText("GOLURI");
                    addText("Peste 2.5: " + percent(over));
                    addText("Sub 2.5: " + percent(under));

                    addText("TOP 10 SCORURI CORECTE");
                    for(int i=0;i<10;i++)
                        if(names[i]!=null)
                            addText((i+1)+". "+names[i]+"  -  "+
                                percent(top[i]));

                    addText("Estimări statistice, nu rezultate garantate.");
                    addText("Date calculate din ultimele meciuri disponibile.");
                });

            } catch(Exception e) {
                runOnUiThread(() -> {
                    list.removeAllViews();
                    addText("Nu s-a putut face analiza: "+e.getMessage());
                });
            }
        }).start();
    }

    String percent(double value) {
        return String.format(Locale.US,"%.1f%%",value*100);
    }

    void addText(String value) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextColor(Color.WHITE);
        t.setTextSize(17);
        t.setPadding(10,12,10,12);
        list.addView(t);
    }
}

