package app.com.example.android.popularmovies;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.GridView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

/**
 * A placeholder fragment containing a simple view.
 */
public class MoviesFragment extends Fragment {

    // Save the poster HTTP Paths and the movie results
    private ArrayList<String> posterPaths = new ArrayList<String>();
    private ArrayList<MovieInfo> movieResults = new ArrayList<MovieInfo>();
    GridView gridview;
    private FetchMoviesTask task;
    GridViewAdapter grid;

    public MoviesFragment() {
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        // Prevents the app from crashing if network not available
        if (!isNetworkAvailable()) {
            Context context = getContext();
            CharSequence text = "Network Not Available!";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
        }
        gridview = (GridView) rootView.findViewById(R.id.gridView);
        return rootView;
    }


    private void updateMovies() {
        SharedPreferences sharedpref = PreferenceManager
                                            .getDefaultSharedPreferences(this.getActivity());

        // Get the Preference settings Popular is default setting
        String sortPref = sharedpref.getString("sort", "popular");
        task = new FetchMoviesTask();
        task.execute(sortPref);
    }

    @Override
    public void onStart() {
        super.onStart();
        updateMovies();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    // If user pauses, delete all the previous data because new data will be loaded in onStart
    @Override
    public void onPause() {
        super.onPause();
        if (posterPaths != null)
            posterPaths.clear();
        if (movieResults != null)
            movieResults.clear();
        if (grid != null)
            grid.clear();
    }

    /*
     * Async Task class to fetch json data from TMDB
     */
    private class FetchMoviesTask extends AsyncTask<String, Void, ArrayList<MovieInfo>> {

        private final String LOG_TAG = FetchMoviesTask.class.getSimpleName();

        // Do background work to fetch thread. Network threads are done on background
        @Override
        protected ArrayList<MovieInfo> doInBackground(String... params) {

            String sortPref = params[0];
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            String moviesJSONRaw = null;

            // try to open internet connection. Catch IOException.
            try {
                // Build the URI for TMDB
                Uri.Builder builder = new Uri.Builder();
                builder.scheme("http")
                        .authority("api.themoviedb.org")
                        .appendPath("3")
                        .appendPath("movie")
                        .appendPath(sortPref)  //  Popular setting
                        .appendQueryParameter("api_key", BuildConfig.OPEN_TMDB_API_KEY);

                URL url = new URL(builder.build().toString());

                urlConnection = (HttpURLConnection) url.openConnection();

                // set the method of request and connect
                urlConnection.setRequestMethod("GET");
                urlConnection.setDoInput(true);
                urlConnection.setDoOutput(true);

                urlConnection.connect();

                // Read input stream into a string
                InputStream inputStream = urlConnection.getInputStream();

                StringBuffer buffer = new StringBuffer();
                if (inputStream == null)
                    return null;

                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null)
                    buffer.append(line + "\n");

                if (buffer.length() == 0)
                    return null;

                moviesJSONRaw = buffer.toString();

            } catch (IOException e) {
                Log.e(LOG_TAG, "Error", e);
                return null;
            } finally {
                // close the connection and reader.
                if (urlConnection != null)
                    urlConnection.disconnect();

                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error Closing Stream", e);
                    }
                }
            }

            try {
                return getMovieDataJson(moviesJSONRaw);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            return null;
        }

        @TargetApi(11)
        @Override
        protected void onPostExecute(ArrayList<MovieInfo> results) {
            grid = new GridViewAdapter(getActivity(), R.layout.fragment_grid, R.id.movie_image, posterPaths);
            gridview.setAdapter(grid);
            grid.notifyDataSetChanged();

            // Implements the onClick listener. The position is the movie index
            gridview.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    Intent intent = new Intent(getActivity(), DetailView.class);
                    intent.putExtra("movie", movieResults.get(position));
                    startActivity(intent);
                }
            });
        }
    }

    // Return strings of movie data
    private ArrayList<MovieInfo> getMovieDataJson(String moviesJSONRaw) throws JSONException {
        // JSON objects that need to be extracted
        final String MDB_RESULT = "results";
        final String MDB_POSTER = "poster_path";
        final String MDB_OVERVIEW = "overview";
        final String MDB_TITLE = "title";
        final String MDB_RELEASE = "release_date";
        final String MDB_RATING = "vote_average";

        JSONObject moviesJson = new JSONObject(moviesJSONRaw);
        JSONArray results = moviesJson.getJSONArray(MDB_RESULT);

        // base url for movie poster
        final String URL_POSTER = "http://image.tmdb.org/t/p/w185/";

        for (int i = 0; i < results.length(); ++i) {
            String plot;
            String title;
            String release;
            String rating;
            String posterUrl;

            // extract all the relevant information from the object
            JSONObject movie = results.getJSONObject(i);
            plot = movie.getString(MDB_OVERVIEW);
            posterUrl = URL_POSTER + movie.getString(MDB_POSTER);
            posterPaths.add(posterUrl);
            title = movie.getString(MDB_TITLE);
            release = movie.getString(MDB_RELEASE);
            rating = movie.getString(MDB_RATING) + "/10";
            // add all the information in one string for parsing later on
            MovieInfo info = new MovieInfo(posterUrl, plot, title, release, rating);
            movieResults.add(info);
        }
        return movieResults;
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) getActivity().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}


