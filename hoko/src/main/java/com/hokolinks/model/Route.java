package com.hokolinks.model;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.hokolinks.utils.log.HokoLog;
import com.hokolinks.utils.networking.Networking;
import com.hokolinks.utils.networking.async.HttpRequest;

import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Route gather information from annotated activity classes, mapping the route format,
 * the route and query parameters to a given class. Also provides methods to create an intent for
 * that particular class with the given parameters and a URL instance. Also provides validation
 * to routes, in order to guarantee all necessary parameters can be mapped.
 */
public class Route {

    // Keys for the Intent.putExtras(...)
    public static final String HokoRouteBundleKey = "Route";
    public static final String HokoRouteRouteParametersBundleKey = "HokoRouteParameters";
    public static final String HokoRouteQueryParametersBundleKey = "HokoQueryParameters";

    private String mRoute;
    private Context mContext;
    private String mActivityClassName;
    private HashMap<String, Field> mRouteParameters;
    private HashMap<String, Field> mQueryParameters;

    /**
     * The constructor for Route objects.
     *
     * @param route             A route in route format.
     * @param activityClassName The activity's class name.
     * @param routeParameters   A HashMap where the keys are route components and the values are
     *                          Fields.
     * @param queryParameters   A HashMap where the keys are query components and the values are
     *                          Fields.
     * @param context           A context to be able to generate the JSON, and the intent.
     */
    public Route(String route, String activityClassName, HashMap<String, Field> routeParameters,
                 HashMap<String, Field> queryParameters, Context context) {
        mRoute = route;
        mActivityClassName = activityClassName;
        mRouteParameters = routeParameters;
        mQueryParameters = queryParameters;
        mContext = context;
    }

    public String getRoute() {
        return mRoute;
    }

    public String getActivityClassName() {
        return mActivityClassName;
    }

    public HashMap<String, Field> getRouteParameters() {
        return mRouteParameters;
    }

    public HashMap<String, Field> getQueryParameters() {
        return mQueryParameters;
    }

    /**
     * Splits the route format into components.
     *
     * @return A List of Route components.
     */
    public List<String> getComponents() {
        if (mRoute == null)
            return null;
        return new ArrayList<String>(Arrays.asList(mRoute.split("/")));
    }

    /**
     * This function serves the purpose of communicating to the Hoko backend service that a given
     * route is available on this application.
     *
     * @param token The Hoko API Token.
     */
    public void post(String token) {
        Networking.getNetworking().addRequest(
                new HttpRequest(HttpRequest.HokoNetworkOperationType.POST, "routes", token,
                        getJSON().toString()));
    }

    /**
     * Converts all the Route information into a JSONObject to be sent to the Hoko backend
     * service.
     *
     * @return The JSONObject representation of Route.
     */
    public JSONObject getJSON() {
        try {
            JSONObject root = new JSONObject();
            JSONObject route = new JSONObject();
            route.put("build", App.getVersionCode(mContext));
            route.put("device", Device.getVendor() + " " + Device.getModel());
            route.put("path", mRoute);
            route.put("version", App.getVersion(mContext));
            root.put("route", route);
            return root;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    /**
     * Retrieves the actual class out of the activity's class name.
     *
     * @return A class object or null.
     */
    private Class getActivityClass() {
        try {
            return Class.forName(mActivityClassName);
        } catch (ClassNotFoundException e) {
            HokoLog.e(e);
        }
        return null;
    }

    /**
     * Generates an Intent out of mapping URL information with the Route object.
     * The intent has 2 bundles, a route parameters bundle which contains a
     * HashMap&lt;String, String&gt; of key value pairs and a query parameters bundle which also
     * contains a HashMap&lt;String, String&gt; of key value pairs. The function also sets a few
     * flags for better deeplinking experience.
     *
     * @param url A URL instance coming from a deeplink.
     * @return The generated intent.
     */
    public Intent getIntent(URL url) {
        Intent intent = new Intent(mContext, getActivityClass());

        Bundle mainBundle = new Bundle();

        mainBundle.putString(HokoRouteBundleKey, this.getRoute());

        // Route Params
        Bundle routeParametersBundle = new Bundle();
        HashMap<String, String> routeParameters = url.matchesWithRoute(this);
        if (routeParameters != null) {
            for (String key : routeParameters.keySet()) {
                String value = routeParameters.get(key);
                routeParametersBundle.putString(key, value);
            }
        }
        mainBundle.putBundle(HokoRouteRouteParametersBundleKey, routeParametersBundle);

        // Query Params
        Bundle queryParametersBundle = new Bundle();
        HashMap<String, String> queryParameters = url.getQueryParameters();
        for (String key : queryParameters.keySet()) {
            String value = queryParameters.get(key);
            queryParametersBundle.putString(key, value);
        }
        mainBundle.putBundle(HokoRouteQueryParametersBundleKey, queryParametersBundle);

        intent.putExtras(mainBundle);

        // Flags for Deeplinking
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

        return intent;
    }

    /**
     * Checks if an instance of Route is actually valid when it comes to mapping route
     * components to the route parameters available.
     *
     * @return true if it's valid, false otherwise.
     */
    public boolean isValid() {
        List<String> routeComponents = getComponents();
        for (String routeComponent : routeComponents) {
            if (routeComponent.startsWith(":")) {
                routeComponent = routeComponent.substring(1);
                if (!getRouteParameters().containsKey(routeComponent))
                    return false;
            }
        }
        return true;
    }

}