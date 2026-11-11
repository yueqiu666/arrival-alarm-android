package com.example.arrivalalarm;

import android.annotation.SuppressLint;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public final class MainActivity extends Activity {
    private static final int REQUEST_CORE_PERMISSIONS = 1001;
    private static final String PRIVACY_POLICY_URL = "https://github.com/yueqiu666/arrival-alarm-android/blob/main/PRIVACY.md";
    private int GREEN;
    private int INK;
    private int MUTED;
    private int PAPER;
    private int SURFACE;
    private int SOFT_BLUE;
    private int SEPARATOR;
    private int DANGER;

    private AppPrefs prefs;
    private PlaceSearchRepository placeSearch;
    private EditText destinationInput;
    private LinearLayout homeIntro;
    private LinearLayout activeCard;
    private TextView activeLabel;
    private TextView activeName;
    private TextView activeMeta;
    private Button activeAction;
    private LinearLayout recentsList;
    private LinearLayout suggestionsList;
    private AppPrefs.Destination pendingDestination;
    private boolean pendingResume;
    private String pendingResumeId;
    private boolean permissionInFlight;
    private boolean permissionGranted;
    private boolean checkingSettings;
    private boolean resumed;
    private Button permissionStatus;
    private TextView storageStatus;
    private String submittedQuery;
    private ShareParser.ParsedShare submittedShare;
    private HomeUiState.Dialog homeDialog;
    private AlertDialog shownHomeDialog;
    private boolean restoreWork;
    private BroadcastReceiver statusReceiver;
    private ReminderRuntime.Snapshot runtimeSnapshot = ReminderRuntime.Snapshot.stopped();
    private ReminderStateLogic.State renderedReminderState;
    private final Handler runtimeHandler = new Handler(Looper.getMainLooper());
    private final Runnable runtimeRefresh = new Runnable() {
        @Override public void run() {
            refreshUi();
            runtimeHandler.postDelayed(this, 10_000L);
        }
    };
    private long activityGeneration;
    private boolean activityStarted;
    private Runnable suggestionDebounce;
    private Future<?> suggestionFuture;
    private Future<?> coordinateFuture;
    private volatile boolean amapAndroidSearchEnabled;
    private final AsyncResultGate suggestionResults = new AsyncResultGate();
    private final AsyncResultGate coordinateResults = new AsyncResultGate();
    private final InteractionGate suggestionRenderGate = new InteractionGate();
    private final InteractionGate recentsRenderGate = new InteractionGate();
    private List<AppPrefs.Destination> localSuggestionCandidates = Collections.emptyList();
    private final ScheduledThreadPoolExecutor placeSearchExecutor = new ScheduledThreadPoolExecutor(2);
    private final Map<String, List<AppPrefs.Destination>> suggestionCache = new LinkedHashMap<String, List<AppPrefs.Destination>>(32, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, List<AppPrefs.Destination>> eldest) { return size() > 32; }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        activityStarted = true;
        activityGeneration = 1L;
        prefs = new AppPrefs(this);
        placeSearch = new PlaceSearchRepository(this);
        disableDeferredAutomation();
        ReminderRuntimeCoordinator.ensureRestoreNotificationChannel(this);
        placeSearchExecutor.setRemoveOnCancelPolicy(true);
        configurePalette();
        getWindow().setStatusBarColor(PAPER);
        getWindow().setNavigationBarColor(PAPER);
        if (savedInstanceState != null) {
            amapAndroidSearchEnabled = AmapAndroidPlaceSearch.isAvailable() && savedInstanceState.getBoolean("amap");
            pendingDestination = HomeUiState.getPlace(savedInstanceState, "pendingDestination");
            pendingResumeId = savedInstanceState.getString("pendingResumeId");
            pendingResume = pendingResumeId != null;
            permissionInFlight = savedInstanceState.getBoolean("permissionInFlight");
            permissionGranted = savedInstanceState.getBoolean("permissionGranted");
            checkingSettings = savedInstanceState.getBoolean("checkingSettings");
            submittedQuery = savedInstanceState.getString("submittedQuery");
            submittedShare = HomeUiState.restoreShare(savedInstanceState.getBundle("submittedShare"));
            homeDialog = HomeUiState.Dialog.restore(savedInstanceState.getBundle("homeDialog"));
            restoreWork = true;
        }
        buildUi();
        if (savedInstanceState == null) {
            handleIntent(getIntent());
        } else {
            destinationInput.setText(savedInstanceState.getString("draft", ""));
            destinationInput.setSelection(Math.min(savedInstanceState.getInt("cursor"), destinationInput.length()));
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putString("draft", destinationInput.getText().toString());
        state.putInt("cursor", Math.max(0, destinationInput.getSelectionEnd()));
        state.putBoolean("amap", amapAndroidSearchEnabled);
        HomeUiState.putPlace(state, "pendingDestination", pendingDestination);
        state.putString("pendingResumeId", pendingResumeId);
        state.putBoolean("permissionInFlight", permissionInFlight);
        state.putBoolean("permissionGranted", permissionGranted);
        state.putBoolean("checkingSettings", checkingSettings);
        state.putString("submittedQuery", submittedQuery);
        if (submittedShare != null) state.putBundle("submittedShare", HomeUiState.saveShare(submittedShare));
        if (homeDialog != null) state.putBundle("homeDialog", homeDialog.save());
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        resumed = true;
        updatePermissionStatus(checkingSettings);
        checkingSettings = false;
        if (permissionGranted) completePermissionRequest();
        if (!restoreWork) return;
        restoreWork = false;
        if (homeDialog != null) {
            if (shownHomeDialog == null) showHomeDialog(homeDialog);
        } else if (!permissionInFlight && submittedShare != null) {
            confirmCoordinateDestination(submittedShare);
        } else if (!permissionInFlight && submittedQuery != null
                && submittedQuery.equals(destinationInput.getText().toString().trim())) {
            searchDestination();
        } else if (!permissionInFlight) {
            updatePlaceSuggestions(destinationInput.getText().toString());
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        super.onPause();
    }

    private void disableDeferredAutomation() {
        prefs.disableDeferredAutomation();
        for (AppPrefs.FixedAlarm alarm : prefs.getFixedAlarms()) {
            ScheduleManager.cancel(this, alarm.id);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!activityStarted) {
            activityStarted = true;
            activityGeneration++;
        }
        runtimeSnapshot = TrackingService.getRuntimeSnapshot();
        statusReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                runtimeSnapshot = TrackingService.getRuntimeSnapshot();
                refreshUi();
            }
        };
        IntentFilter filter = new IntentFilter(TrackingService.ACTION_STATUS);
        registerStatusReceiver(filter);
        refreshUi();
        if (destinationInput != null) destinationInput.setEnabled(true);
        runtimeHandler.post(runtimeRefresh);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void registerStatusReceiver(IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(statusReceiver, filter, TrackingService.PERMISSION_STATUS, null, Context.RECEIVER_NOT_EXPORTED);
            return;
        }
        // RECEIVER_NOT_EXPORTED does not exist before API 33; the signature permission
        // provides the equivalent sender restriction for the supported API 26-32 range.
        registerReceiver(statusReceiver, filter, TrackingService.PERMISSION_STATUS, null);
    }

    @Override
    protected void onStop() {
        restoreWork = true;
        activityStarted = false;
        activityGeneration++;
        cancelActivityAsyncWork();
        super.onStop();
        runtimeHandler.removeCallbacks(runtimeRefresh);
        if (statusReceiver != null) unregisterReceiver(statusReceiver);
        statusReceiver = null;
    }

    @Override
    protected void onDestroy() {
        activityStarted = false;
        activityGeneration++;
        cancelActivityAsyncWork();
        dismissHomeDialog();
        placeSearchExecutor.shutdownNow();
        super.onDestroy();
    }

    private void cancelActivityAsyncWork() {
        suggestionResults.invalidate();
        coordinateResults.invalidate();
        if (suggestionDebounce != null && destinationInput != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        if (coordinateFuture != null) coordinateFuture.cancel(true);
        suggestionDebounce = null;
        suggestionFuture = null;
        coordinateFuture = null;
    }

    private boolean isActivityCallbackValid(long generation) {
        return activityStarted && generation == activityGeneration
                && !isFinishing() && !isDestroyed();
    }

    private boolean isActivityCallbackValid(long activityToken, long resultToken, AsyncResultGate results) {
        return isActivityCallbackValid(activityToken) && results.isCurrent(resultToken);
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setBackgroundColor(PAPER);
        LinearLayout root = column();
        applySafeArea(root);
        scroll.addView(root, matchWrap());

        LinearLayout header = row();
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text(getString(R.string.app_name), 21, Typeface.BOLD, INK);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        Button settingsButton = actionButton(getString(R.string.settings), 14, INK,
                Color.TRANSPARENT, Color.TRANSPARENT);
        settingsButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showSettings();
            }
        });
        header.addView(brand, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        header.addView(settingsButton, wrapWrap());
        root.addView(header, matchWrap());

        int panelColor = getColor(R.color.home_panel);
        int panelNote = getColor(R.color.home_panel_note);
        LinearLayout panel = column();
        panel.setBackground(roundRect(panelColor, 20, Color.TRANSPARENT));
        panel.setClipToOutline(true);
        LinearLayout.LayoutParams panelParams = matchWrap();
        panelParams.topMargin = dp(16);
        root.addView(panel, panelParams);

        int bannerHeight = dp(144);
        FrameLayout banner = new FrameLayout(this);
        ImageView stationPhoto = new ImageView(this);
        stationPhoto.setImageResource(R.drawable.home_station_photo);
        stationPhoto.setScaleType(ImageView.ScaleType.CENTER_CROP);
        stationPhoto.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        int panelRgb = panelColor & 0x00ffffff;
        stationPhoto.setForeground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xc0000000 | panelRgb, 0x30000000 | panelRgb}));
        banner.addView(stationPhoto, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, bannerHeight));
        LinearLayout bannerContent = column();
        bannerContent.setMinimumHeight(bannerHeight);
        bannerContent.setGravity(Gravity.CENTER_VERTICAL);
        bannerContent.setPadding(dp(16), dp(18), dp(16), dp(18));
        banner.addView(bannerContent, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        panel.addView(banner, matchWrap());

        homeIntro = column();
        TextView title = text(getString(R.string.home_title), 30, Typeface.BOLD, Color.WHITE);
        TextView tagline = text(getString(R.string.home_tagline), 14, Typeface.NORMAL, panelNote);
        tagline.setPadding(0, dp(4), 0, 0);
        homeIntro.addView(title, matchWrap());
        homeIntro.addView(tagline, matchWrap());
        bannerContent.addView(homeIntro, matchWrap());

        activeCard = row();
        activeCard.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout activeCopy = column();
        activeLabel = text("", 13, Typeface.BOLD, panelNote);
        activeName = text("", 24, Typeface.BOLD, Color.WHITE);
        activeMeta = text("", 14, Typeface.NORMAL, panelNote);
        activeName.setPadding(0, dp(4), 0, dp(4));
        activeCopy.addView(activeLabel, matchWrap());
        activeCopy.addView(activeName, matchWrap());
        activeCopy.addView(activeMeta, matchWrap());
        activeAction = actionButton(getString(R.string.stop), 14, Color.WHITE, panelColor,
                getColor(R.color.home_panel_outline));
        activeAction.setOnClickListener(view -> {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
            if (renderedReminderState == ReminderStateLogic.State.NEEDS_RESUME) resumeReminder();
            else stopReminder();
        });
        LinearLayout.LayoutParams activeCopyParams = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        activeCopyParams.setMarginEnd(dp(12));
        activeCard.addView(activeCopy, activeCopyParams);
        activeCard.addView(activeAction, wrapWrap());
        bannerContent.addView(activeCard, matchWrap());

        LinearLayout search = row();
        search.setGravity(Gravity.CENTER_VERTICAL);
        search.setPadding(dp(4), dp(4), dp(4), dp(4));
        search.setBackground(roundRect(SURFACE, 14, Color.TRANSPARENT));
        destinationInput = new EditText(this);
        destinationInput.setFilters(new InputFilter[]{(source, startIndex, endIndex, dest, dstart, dend) -> {
            if (dest.length() - (dend - dstart) + endIndex - startIndex <= PlaceSearchLimits.MAX_INPUT_CHARS) return null;
            toast("输入过长，请使用较短的地点名称或分享链接");
            return dest.subSequence(dstart, dend); // Reject the entire edit; never search a truncated destination.
        }});
        destinationInput.setSingleLine(true);
        destinationInput.setTextSize(16);
        destinationInput.setTextColor(INK);
        destinationInput.setHintTextColor(MUTED);
        destinationInput.setHint(R.string.destination_hint);
        destinationInput.setMinHeight(dimension(R.dimen.minimum_touch_target));
        destinationInput.setBackgroundColor(Color.TRANSPARENT);
        destinationInput.setPadding(dp(12), 0, dp(8), 0);
        destinationInput.setImeOptions(EditorInfo.IME_ACTION_GO);
        destinationInput.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO) { searchDestination(); return true; }
                return false;
            }
        });
        destinationInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                if (restoreWork) return;
                submittedQuery = null;
                submittedShare = null;
                coordinateResults.invalidate();
                if (coordinateFuture != null) coordinateFuture.cancel(true);
                updatePlaceSuggestions(value.toString());
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        Button start = actionButton(getString(R.string.start_search), 15, Color.WHITE,
                panelColor, Color.TRANSPARENT);
        start.setMinWidth(dp(64));
        start.setBackground(roundRect(panelColor, 10, Color.TRANSPARENT));
        start.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                searchDestination();
            }
        });
        search.addView(destinationInput, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        search.addView(start, wrapWrap());
        LinearLayout.LayoutParams searchParams = matchWrap();
        searchParams.setMargins(dp(16), dp(12), dp(16), dp(16));
        panel.addView(search, searchParams);

        suggestionsList = column();
        suggestionsList.setVisibility(View.GONE);
        suggestionsList.setPadding(dp(6), dp(6), dp(6), dp(6));
        suggestionsList.setBackground(roundRect(SURFACE, 14, SEPARATOR));
        LinearLayout.LayoutParams suggestionParams = matchWrap();
        suggestionParams.topMargin = dp(6);
        root.addView(suggestionsList, suggestionParams);

        if (AmapAndroidPlaceSearch.isAvailable()) {
            Button searchSource = secondaryButton(searchSourceLabel());
            searchSource.setOnClickListener(view -> chooseSearchSource(searchSource));
            root.addView(searchSource, matchWrap());
        }
        permissionStatus = secondaryButton("");
        permissionStatus.setOnClickListener(view -> openPermissionSettings());
        permissionStatus.setVisibility(View.GONE);
        root.addView(permissionStatus, matchWrap());
        storageStatus = text(getString(R.string.storage_unreadable), 14, Typeface.NORMAL, DANGER);
        storageStatus.setPadding(0, dp(8), 0, dp(8));
        storageStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        storageStatus.setVisibility(View.GONE);
        root.addView(storageStatus, matchWrap());

        TextView recentTitle = text(getString(R.string.recent_places), 17, Typeface.BOLD, INK);
        LinearLayout.LayoutParams recentTitleParams = matchWrap();
        recentTitleParams.topMargin = dp(24);
        recentTitleParams.bottomMargin = dp(6);
        root.addView(recentTitle, recentTitleParams);
        recentsList = column();
        root.addView(recentsList, matchWrap());

        Button privacy = actionButton(getString(R.string.privacy_title), 13, MUTED,
                Color.TRANSPARENT, Color.TRANSPARENT);
        privacy.setPadding(0, dp(8), 0, dp(8));
        privacy.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                showPrivacy();
            }
        });
        LinearLayout.LayoutParams privacyParams = wrapWrap();
        privacyParams.topMargin = dp(12);
        root.addView(privacy, privacyParams);

        setContentView(scroll);
        refreshUi();
        animateEntrance(root);
    }

    private void searchDestination() {
        final String query = destinationInput.getText().toString().trim();
        if (query.isEmpty()) { destinationInput.requestFocus(); return; }
        final long resultToken = suggestionResults.begin();
        if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        hideKeyboard();
        final ShareParser.ParsedShare parsed = ShareParser.parseText(query);
        if (parsed != null && parsed.hasCoordinates()) {
            confirmCoordinateDestination(parsed);
            return;
        }
        if (!PlaceSearchLimits.validQuery(query)) {
            renderSuggestionMessage("搜索词过长，请使用城市和地点名称（最多 200 字）");
            return;
        }
        submittedQuery = query;
        renderSuggestionMessage(amapAndroidSearchEnabled ? "高德正在搜索…"
                : !PlaceSearchRepository.developerAmapKey().isEmpty() ? "高德正在确认地点…" : "地图正在确认地点…");
        final boolean amapTrial = amapAndroidSearchEnabled;
        final long activityToken = activityGeneration;
        suggestionFuture = placeSearchExecutor.submit(new Runnable() {
            @Override public void run() {
                final PlaceSearchPipeline.Result<AppPrefs.Destination> queryResult = placeSearch.query(query, 7, false, amapTrial);
                final List<AppPrefs.Destination> results = queryResult.places;
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isActivityCallbackValid(activityToken, resultToken, suggestionResults)) return;
                        if (!query.equals(destinationInput.getText().toString().trim())) return;
                        submittedQuery = null;
                        if (results.isEmpty()) {
                            renderSearchOutcome(queryResult);
                            return;
                        }
                        renderPlaceSuggestions(results);
                        showDestinationChoices(results);
                    }
                });
            }
        });
    }

    private void showDestinationChoices(List<AppPrefs.Destination> places) {
        if (places.size() == 1) {
            showDestinationConfirmation(places.get(0), getString(R.string.search_selected_place), null);
        } else {
            showHomeDialog(new HomeUiState.Dialog(HomeUiState.DialogKind.CHOICES, getString(R.string.search_selected_place), null, null, places));
        }
    }

    private void showDestinationConfirmation(AppPrefs.Destination place, String title, List<AppPrefs.Destination> choices) {
        String details = DestinationConfirmation.details(place.name, place.city, place.address,
                place.latitude, place.longitude, place.id.startsWith("inferred-"));
        showHomeDialog(new HomeUiState.Dialog(HomeUiState.DialogKind.CONFIRM, title, details, place, choices));
    }

    private void showHomeDialog(HomeUiState.Dialog state) {
        dismissHomeDialog();
        homeDialog = state;
        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(state.title).setMessage(state.message);
        switch (state.kind) {
            case CHOICES:
                builder.setItems(destinationLabels(state.choices), (dialog, index) ->
                        showDestinationConfirmation(state.choices.get(index), getString(R.string.verify_destination), state.choices));
                builder.setNegativeButton(getString(R.string.cancel), null);
                break;
            case CONFIRM:
                builder.setNegativeButton(getString(R.string.cancel), (dialog, which) -> returnToChoices(state));
                builder.setOnCancelListener(dialog -> returnToChoices(state));
                builder.setPositiveButton(getString(R.string.use_destination), (dialog, which) -> beginDestination(state.place));
                break;
            case REPLACE:
                builder.setNegativeButton(getString(R.string.cancel), null);
                builder.setPositiveButton(getString(R.string.replace), (dialog, which) -> requestStart(state.place));
                break;
            case PERMISSIONS:
                builder.setNegativeButton(getString(R.string.permission_later), (dialog, which) -> clearPendingPermission());
                builder.setOnCancelListener(dialog -> clearPendingPermission());
                builder.setPositiveButton(getString(R.string.permission_continue), (dialog, which) -> requestCorePermissionsNow());
                break;
        }
        AlertDialog dialog = showStyledDialog(builder);
        shownHomeDialog = dialog;
        dialog.setOnDismissListener(ignored -> {
            if (shownHomeDialog == dialog) {
                shownHomeDialog = null;
                homeDialog = null;
            }
        });
    }

    private void returnToChoices(HomeUiState.Dialog state) {
        if (state.choices != null && !state.choices.isEmpty()) showDestinationChoices(state.choices);
    }

    private void dismissHomeDialog() {
        AlertDialog previous = shownHomeDialog;
        shownHomeDialog = null;
        homeDialog = null;
        if (previous != null) previous.dismiss();
    }

    private String searchSourceLabel() {
        return amapAndroidSearchEnabled ? getString(R.string.search_source_amap) : getString(R.string.search_source_existing);
    }

    private void chooseSearchSource(Button button) {
        if (amapAndroidSearchEnabled) {
            setAmapAndroidSearchEnabled(button, false);
            return;
        }
        String disclosure = "高德搜索 SDK 由北京高德图强科技有限公司提供，用于搜索地点。"
                + "启用后，高德会接收你输入的搜索词。SDK 还会处理应用名称和版本、设备品牌和型号、"
                + "操作系统及运营商信息，用于鉴权、诊断和判断网络状态。应用不主动发送附近位置，"
                + "并关闭 SDK 可配置的个人及设备信息采集。\n\n"
                + "可用“城市+地点”限制搜索，例如“成都市+天府广场站”。再次点击搜索来源可切回现有服务。";
        showStyledDialog(new AlertDialog.Builder(this).setTitle("启用高德搜索？").setMessage(disclosure)
                .setNeutralButton("高德隐私政策", (dialog, which) -> {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://lbs.amap.com/pages/privacy/"))); }
                    catch (Exception ignored) { toast("暂时无法打开高德隐私政策"); }
                })
                .setNegativeButton("继续使用现有搜索", null)
                .setPositiveButton("同意并启用", (dialog, which) -> setAmapAndroidSearchEnabled(button, true)));
    }

    private void setAmapAndroidSearchEnabled(Button button, boolean enabled) {
        suggestionResults.invalidate();
        if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        amapAndroidSearchEnabled = enabled;
        submittedQuery = null;
        button.setText(searchSourceLabel());
        suggestionRenderGate.reset();
        suggestionsList.setVisibility(View.GONE);
    }

    private String[] destinationLabels(List<AppPrefs.Destination> places) {
        List<String> labels = new ArrayList<>();
        for (AppPrefs.Destination place : places) {
            labels.add(place.name + (place.city.isEmpty() ? "" : " · " + place.city)
                    + (place.address.isEmpty() ? "" : "\n" + place.address));
        }
        return labels.toArray(new String[0]);
    }

    private void updatePlaceSuggestions(String raw) {
        final String query = raw.trim();
        final long resultToken = suggestionResults.begin();
        final long activityToken = activityGeneration;
        if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
        if (suggestionFuture != null) suggestionFuture.cancel(true);
        if (suggestionsList == null || query.isEmpty()) {
            suggestionRenderGate.reset();
            if (suggestionsList != null && suggestionsList.getVisibility() != View.GONE) suggestionsList.setVisibility(View.GONE);
            return;
        }
        // Share links and coordinates are resolved only by the explicit confirmation path.
        if (!PlaceSearchLimits.validQuery(query)) {
            renderSuggestionMessage("输入较长，请点击开始解析分享链接，或缩短搜索词");
            return;
        }
        final boolean amapTrial = amapAndroidSearchEnabled;
        List<AppPrefs.Destination> local = amapTrial ? Collections.emptyList() : localPlaceSuggestions(query);
        String cacheKey = query.toLowerCase(Locale.CHINA);
        List<AppPrefs.Destination> cached = amapTrial ? null : suggestionCache.get(cacheKey);
        if (cached != null) local = mergePlaces(local, cached, 6);
        if (!local.isEmpty()) renderPlaceSuggestions(local);
        boolean amapEnabled = !PlaceSearchRepository.developerAmapKey().isEmpty();
        if (cached != null) {
            if (local.isEmpty()) renderSuggestionMessage("暂未找到，试试“城市 + 地点名”");
            return;
        }
        if (query.length() < 2 && !amapEnabled) {
            if (local.isEmpty()) renderSuggestionMessage("再输入一个字，自动推荐相关地点");
            return;
        }
        if (local.isEmpty()) renderSuggestionMessage(amapTrial ? "高德 Android SDK 正在联想…"
                : amapEnabled ? "高德正在智能联想…" : "地图正在智能联想…");
        suggestionDebounce = new Runnable() {
            @Override public void run() {
                if (!isActivityCallbackValid(activityToken, resultToken, suggestionResults)) return;
                suggestionFuture = placeSearchExecutor.submit(new Runnable() {
                    @Override public void run() {
                        final PlaceSearchPipeline.Result<AppPrefs.Destination> queryResult = placeSearch.query(query, 4, true, amapTrial);
                        final List<AppPrefs.Destination> remote = queryResult.places;
                        runOnUiThread(new Runnable() {
                            @Override public void run() {
                                if (!isActivityCallbackValid(activityToken, resultToken, suggestionResults)
                                        || !query.equals(destinationInput.getText().toString().trim())) return;
                                if (!amapTrial) {
                                    if (queryResult.cacheable()) {
                                        suggestionCache.put(cacheKey, new ArrayList<>(remote));
                                    } else {
                                        suggestionCache.remove(cacheKey);
                                    }
                                }
                                List<AppPrefs.Destination> merged = amapTrial ? remote : mergePlaces(localPlaceSuggestions(query), remote, 4);
                                if (merged.isEmpty()) renderSearchOutcome(queryResult);
                                else renderPlaceSuggestions(merged);
                            }
                        });
                    }
                });
            }
        };
        destinationInput.postDelayed(suggestionDebounce, InteractionGate.searchDebounceMillis(amapEnabled));
    }

    private List<AppPrefs.Destination> mergePlaces(List<AppPrefs.Destination> first,
                                                    List<AppPrefs.Destination> second, int maxResults) {
        List<AppPrefs.Destination> merged = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (AppPrefs.Destination item : first) if (ids.add(item.id) && merged.size() < maxResults) merged.add(item);
        for (AppPrefs.Destination item : second) if (ids.add(item.id) && merged.size() < maxResults) merged.add(item);
        return merged;
    }

    private List<AppPrefs.Destination> localPlaceSuggestions(String query) {
        List<AppPrefs.Destination> results = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String needle = query.toLowerCase(Locale.CHINA);
        for (AppPrefs.Destination item : localSuggestionCandidates) {
            String text = (item.name + " " + item.city + " " + item.address).toLowerCase(Locale.CHINA);
            if (text.contains(needle) && ids.add(item.id)) results.add(item);
            if (results.size() >= 4) break;
        }
        return results;
    }

    private void renderPlaceSuggestions(List<AppPrefs.Destination> places) {
        if (suggestionsList == null) return;
        StringBuilder renderKey = new StringBuilder("places:");
        for (AppPrefs.Destination place : places) renderKey.append(place.id).append('|');
        String nextKey = renderKey.toString();
        boolean visible = suggestionsList.getVisibility() == View.VISIBLE;
        if (!suggestionRenderGate.shouldRender(nextKey, visible, !places.isEmpty())) return;
        suggestionsList.removeAllViews();
        suggestionsList.setVisibility(places.isEmpty() ? View.GONE : View.VISIBLE);
        if (places.isEmpty()) return;
        boolean amap = places.get(0).id.startsWith("amap-");
        boolean photon = places.get(0).id.startsWith("photon-");
        boolean inferred = places.get(0).id.startsWith("inferred-");
        TextView header = text(amapAndroidSearchEnabled ? "高德地图" : amap ? "高德智能推荐" : photon ? "OpenStreetMap 地点推荐"
                : inferred ? "系统推测 · 请核对地址" : "相关地点", 12, Typeface.BOLD, MUTED);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(12), 0);
        suggestionsList.addView(header, matchWrap());
        for (int i = 0; i < places.size(); i++) {
            final AppPrefs.Destination place = places.get(i);
            LinearLayout item = row();
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setPadding(dp(10), dp(5), dp(8), dp(5));
            TextView marker = text("●", 13, Typeface.BOLD, GREEN);
            marker.setGravity(Gravity.CENTER);
            marker.setBackground(roundRect(SOFT_BLUE, 12, Color.TRANSPARENT));
            LinearLayout copy = column();
            TextView name = text(place.name, 15, Typeface.BOLD, INK);
            String details = place.city + (!place.city.isEmpty() && !place.address.isEmpty() ? " · " : "") + place.address;
            TextView detail = text(details.isEmpty() ? getString(R.string.unknown_address) : details, 12, Typeface.NORMAL, MUTED);
            detail.setPadding(0, dp(2), 0, 0);
            copy.addView(name, matchWrap());
            copy.addView(detail, matchWrap());
            TextView select = text(getString(R.string.select_candidate), 13, Typeface.BOLD, GREEN);
            select.setGravity(Gravity.CENTER);
            item.addView(marker, new LinearLayout.LayoutParams(dp(36), dp(36)));
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            copyParams.leftMargin = dp(10);
            item.addView(copy, copyParams);
            item.addView(select, wrapWrap());
            item.setMinimumHeight(dimension(R.dimen.minimum_touch_target));
            item.setFocusable(true);
            item.setContentDescription(getString(R.string.verify_place_accessibility, place.name, details));
            marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            copy.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            select.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            item.setOnClickListener(view -> {
                view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                suggestionResults.invalidate();
                if (suggestionDebounce != null) destinationInput.removeCallbacks(suggestionDebounce);
                if (suggestionFuture != null) suggestionFuture.cancel(true);
                hideKeyboard();
                submittedQuery = null;
                showDestinationConfirmation(place, getString(R.string.verify_destination), null);
            });
            applyPressFeedback(item);
            suggestionsList.addView(item, matchWrap());
            if (i < places.size() - 1) {
                View divider = new View(this);
                divider.setBackgroundColor(SEPARATOR);
                LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1));
                dividerParams.leftMargin = dp(56);
                suggestionsList.addView(divider, dividerParams);
            }
        }
    }

    private void renderSuggestionMessage(String message) {
        renderSuggestionMessage(message, false);
    }

    private void renderSearchOutcome(PlaceSearchPipeline.Result<AppPrefs.Destination> result) {
        renderSuggestionMessage(result.emptyMessage(), result.status == PlaceSearchPipeline.Status.UNAVAILABLE);
    }

    private void renderSuggestionMessage(String message, boolean unavailable) {
        if (suggestionsList == null) return;
        String nextKey = "message:" + message;
        if (!suggestionRenderGate.shouldRender(nextKey,
                suggestionsList.getVisibility() == View.VISIBLE, true)) return;
        suggestionsList.removeAllViews();
        suggestionsList.setVisibility(View.VISIBLE);
        TextView status = text(message, 13, Typeface.NORMAL, unavailable ? DANGER : MUTED);
        status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(dp(12), dp(8), dp(12), dp(8));
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        suggestionsList.addView(status, matchWrap());
        if (unavailable) {
            Button retry = secondaryButton(getString(R.string.retry_search));
            retry.setOnClickListener(view -> searchDestination());
            suggestionsList.addView(retry, matchWrap());
        }
    }

    private void beginDestination(final AppPrefs.Destination destination) {
        if (prefs.getTask() != null) {
            showHomeDialog(new HomeUiState.Dialog(HomeUiState.DialogKind.REPLACE, getString(R.string.replace_reminder),
                    getString(R.string.replace_reminder_message), destination, null));
        } else requestStart(destination);
    }

    private void requestStart(AppPrefs.Destination destination) {
        clearPendingPermission();
        pendingDestination = destination;
        if (!hasCorePermissions()) { requestCorePermissions(); return; }
        startPendingDestination();
    }

    private void startPendingDestination() {
        if (pendingDestination == null) return;
        AppPrefs.Destination destination = pendingDestination;
        boolean started = ReminderRuntimeCoordinator.startDestination(this, destination);
        destinationInput.setText("");
        pendingDestination = null;
        runtimeSnapshot = TrackingService.getRuntimeSnapshot();
        refreshUi();
        toast(started ? "正在启动守候 " + destination.name : "守候启动失败，请检查权限和定位设置");
    }

    private void stopReminder() {
        ReminderRuntimeCoordinator.stopReminder(this);
        runtimeSnapshot = TrackingService.getRuntimeSnapshot();
        renderedReminderState = null;
        refreshUi();
        toast("已停止提醒");
    }

    private void resumeReminder() {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null) return;
        if (!hasCorePermissions()) {
            clearPendingPermission();
            pendingResume = true;
            pendingResumeId = task.id;
            requestCorePermissions();
            return;
        }
        resumeReminderNow();
    }

    private void resumeReminderNow() {
        AppPrefs.ReminderTask task = prefs.getTask();
        if (task == null) return;
        boolean started = ReminderRuntimeCoordinator.resumeReminder(this);
        runtimeSnapshot = TrackingService.getRuntimeSnapshot();
        refreshUi();
        toast(started ? "正在恢复守候 " + task.destination.name : "恢复失败，请检查权限和定位设置");
    }

    private void requestCorePermissions() {
        showHomeDialog(new HomeUiState.Dialog(HomeUiState.DialogKind.PERMISSIONS,
                getString(R.string.allow_permissions),
                getString(R.string.allow_permissions_message),
                null, null));
    }

    private void requestCorePermissionsNow() {
        String[] permissions = ReminderPermissions.missing(this);
        if (permissions.length == 0) {
            permissionGranted = true;
            if (resumed) completePermissionRequest();
            return;
        }
        permissionInFlight = true;
        requestPermissions(permissions, REQUEST_CORE_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_CORE_PERMISSIONS) return;
        permissionInFlight = false;
        updatePermissionStatus(false);
        if (!ReminderPermissions.hasLocation(this)) {
            clearPendingPermission();
            toast(getString(R.string.permission_location_required));
            return;
        }
        if (!ReminderPermissions.hasNotifications(this)) {
            clearPendingPermission();
            toast(getString(R.string.permission_notification_required));
            return;
        }
        if (grantResults.length == 0) {
            clearPendingPermission();
            return;
        }
        permissionGranted = true;
        if (resumed) completePermissionRequest();
    }

    private void completePermissionRequest() {
        permissionGranted = false;
        if (!hasCorePermissions()) {
            clearPendingPermission();
            return;
        }
        if (pendingResume) {
            AppPrefs.ReminderTask task = prefs.getTask();
            boolean sameTask = task != null && task.isInterrupted() && task.id.equals(pendingResumeId);
            clearPendingPermission();
            if (sameTask) resumeReminderNow();
            return;
        }
        startPendingDestination();
    }

    private void clearPendingPermission() {
        pendingDestination = null;
        pendingResume = false;
        pendingResumeId = null;
        permissionGranted = false;
        permissionInFlight = false;
    }

    private void openPermissionSettings() {
        try {
            checkingSettings = true;
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getPackageName(), null)));
        } catch (android.content.ActivityNotFoundException unavailable) {
            checkingSettings = false;
            toast(getString(R.string.permission_settings_unavailable));
        }
    }

    private void updatePermissionStatus(boolean returning) {
        if (permissionStatus == null) return;
        boolean location = ReminderPermissions.hasLocation(this);
        boolean notifications = ReminderPermissions.hasNotifications(this);
        permissionStatus.setVisibility(location && notifications ? View.GONE : View.VISIBLE);
        permissionStatus.setText(!location && !notifications ? getString(R.string.permission_settings_missing_both)
                : !location ? getString(R.string.permission_settings_missing_location) : getString(R.string.permission_settings_missing_notifications));
        if (returning) toast(location && notifications ? getString(R.string.permission_settings_ready)
                : getString(R.string.permission_settings_incomplete));
    }

    private boolean hasCorePermissions() { return ReminderRuntimeCoordinator.hasCorePermissions(this); }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (ShareParser.isInputTooLong(intent)) {
            toast("分享内容过长，请使用较短的地点名称或分享链接");
            return;
        }
        ShareParser.ParsedShare shared = ShareParser.fromIntent(intent);
        if (shared == null) {
            if (Intent.ACTION_SEND.equals(intent.getAction())) toast("未能识别分享链接，请直接输入地点名称");
            return;
        }
        dismissHomeDialog();
        clearPendingPermission();
        cancelActivityAsyncWork();
        submittedQuery = null;
        submittedShare = null;
        if (shared.hasCoordinates()) {
            confirmCoordinateDestination(shared);
        } else if (!shared.name.isEmpty()) {
            destinationInput.setText(shared.name);
            destinationInput.setSelection(shared.name.length());
            searchDestination();
        }
    }

    private void confirmCoordinateDestination(final ShareParser.ParsedShare parsed) {
        submittedQuery = null;
        submittedShare = parsed;
        final String name = parsed.name.isEmpty() ? "分享的目的地" : parsed.name;
        final double latitude = parsed.latitude;
        final double longitude = parsed.longitude;
        destinationInput.setEnabled(false);
        final long activityToken = activityGeneration;
        final long resultToken = coordinateResults.begin();
        if (coordinateFuture != null) coordinateFuture.cancel(true);
        coordinateFuture = placeSearchExecutor.submit(new Runnable() {
            @Override public void run() {
                final String address = placeSearch.reverseAddress(latitude, longitude);
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (!isActivityCallbackValid(activityToken, resultToken, coordinateResults)) return;
                        destinationInput.setEnabled(true);
                        submittedShare = null;
                        String coordinate = String.format(Locale.CHINA, "%.6f, %.6f", latitude, longitude);
                        String verification = address.isEmpty() ? "系统无法反查地址，请确认地点名称和坐标。" : "系统反查：" + address;
                        String coordinateSystem = parsed.coordinateSystemVerified
                                ? "坐标系：已按来源转换到 WGS-84"
                                : "坐标系：未能识别来源，已按原值使用，请核对下面的地址";
                        String message = name + "\n" + verification + "\n来源：" + parsed.source + "\n" + coordinateSystem + "\n坐标：" + coordinate;
                        String savedAddress = address.isEmpty() ? parsed.source : address;
                        AppPrefs.Destination destination = new AppPrefs.Destination(
                                PlaceSearchRepository.idFor(name, latitude, longitude), name, savedAddress, "", latitude, longitude);
                        showHomeDialog(new HomeUiState.Dialog(HomeUiState.DialogKind.CONFIRM, getString(R.string.verify_destination),
                                message, destination, null));
                    }
                });
            }
        });
    }

    private void refreshUi() {
        if (activeCard == null) return;
        runtimeSnapshot = TrackingService.getRuntimeSnapshot();
        ReminderRuntimeCoordinator.Status reminderStatus = ReminderRuntimeCoordinator.reconcile(
                this, runtimeSnapshot, SystemClock.elapsedRealtime());
        AppPrefs.ReminderTask task = reminderStatus.task;
        ReminderStateLogic.State state = reminderStatus.state;
        homeIntro.setVisibility(task == null ? View.VISIBLE : View.GONE);
        if (task == null) {
            ReminderRuntimeCoordinator.cancelRestoreNotification(this);
            renderedReminderState = null;
            activeCard.setVisibility(View.GONE);
        } else {
            activeCard.setVisibility(View.VISIBLE);
            activeName.setText(task.destination.name);
            boolean needsResume = state == ReminderStateLogic.State.NEEDS_RESUME;
            activeAction.setText(needsResume ? R.string.resume : R.string.stop);
            activeLabel.setTextColor(getColor(needsResume ? R.color.home_panel_warning : R.color.home_panel_note));
            switch (state) {
                case NEEDS_RESUME:
                    activeLabel.setText(R.string.status_needs_resume);
                    activeMeta.setText(R.string.meta_resume);
                    break;
                case STARTING:
                    activeLabel.setText(R.string.status_starting);
                    activeMeta.setText(R.string.meta_starting);
                    break;
                case RECOVERING:
                    activeLabel.setText(R.string.status_recovering);
                    activeMeta.setText(R.string.meta_recovering);
                    break;
                case WAITING_LOCATION:
                    activeLabel.setText(R.string.status_waiting);
                    activeMeta.setText(getString(R.string.meta_awaiting_first_fix, formatRadius(task.radiusMeters)));
                    break;
                case ALERTING:
                    activeLabel.setText(R.string.status_arrived);
                    activeMeta.setText(R.string.meta_arrived);
                    break;
                default:
                    activeLabel.setText(R.string.status_monitoring);
                    String radius = formatRadius(task.radiusMeters);
                    activeMeta.setText(Double.isNaN(task.lastDistanceMeters)
                            ? getString(R.string.meta_waiting_location, radius)
                            : getString(R.string.meta_distance_remaining, formatDistance(task.lastDistanceMeters), radius));
            }
            if (needsResume && renderedReminderState != state) {
                ReminderRuntimeCoordinator.showRestoreNotification(this, task, "");
            } else if (!needsResume) {
                ReminderRuntimeCoordinator.cancelRestoreNotification(this);
            }
            renderedReminderState = state;
        }
        List<AppPrefs.Destination> recents = prefs.getRecents();
        storageStatus.setVisibility(prefs.hasUnreadableData() ? View.VISIBLE : View.GONE);
        localSuggestionCandidates = new ArrayList<>(recents);
        renderRecents(recents);
    }

    private void renderRecents(List<AppPrefs.Destination> recents) {
        if (recentsList == null) return;
        StringBuilder key = new StringBuilder();
        for (AppPrefs.Destination place : recents) key.append(place.id).append('|').append(place.name).append(';');
        String nextKey = key.toString();
        if (!recentsRenderGate.shouldRender(nextKey, recentsList.getChildCount() > 0, true)) return;
        recentsList.removeAllViews();
        if (recents.isEmpty()) {
            TextView empty = text(getString(R.string.no_recents), 14, Typeface.NORMAL, MUTED);
            empty.setGravity(Gravity.CENTER_VERTICAL);
            empty.setMinHeight(dimension(R.dimen.minimum_touch_target));
            recentsList.addView(empty, matchWrap());
            return;
        }
        for (final AppPrefs.Destination place : recents) {
            if (recentsList.getChildCount() > 0) {
                View divider = new View(this);
                divider.setBackgroundColor(SEPARATOR);
                recentsList.addView(divider, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
            }
            Button item = actionButton(place.name, 15, INK, Color.TRANSPARENT, Color.TRANSPARENT);
            item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            item.setPaddingRelative(dp(2), dp(8), dp(2), dp(8));
            item.setMinHeight(dp(54));
            item.setMaxLines(2);
            item.setEllipsize(TextUtils.TruncateAt.END);
            item.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_home_place, 0, 0, 0);
            item.setCompoundDrawablePadding(dp(12));
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View view) { beginDestination(place); }
            });
            recentsList.addView(item, matchWrap());
        }
    }

    private void showSettings() {
        LinearLayout content = column();
        content.setPadding(dp(18), dp(8), dp(18), dp(8));
        final TextView radiusLabel = text(getString(R.string.radius_label, formatRadius(prefs.getRadius())), 15, Typeface.BOLD, INK);
        final SeekBar radius = new SeekBar(this);
        radius.setId(View.generateViewId());
        radiusLabel.setLabelFor(radius.getId());
        radius.setMinimumHeight(dimension(R.dimen.minimum_touch_target));
        radius.setMax(47);
        radius.setProgress((prefs.getRadius() - 300) / 100);
        radius.setProgressTintList(ColorStateList.valueOf(GREEN));
        radius.setThumbTintList(ColorStateList.valueOf(GREEN));
        radius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { radiusLabel.setText(getString(R.string.radius_label, formatRadius(300 + progress * 100))); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        TextView privacy = text("地点搜索会使用地图 POI 服务。搜索关键词及可选的附近坐标会发送给地点服务；导入带坐标的目的地会先交给系统 Geocoder 反查地址；到站距离只在本机计算。", 13, Typeface.NORMAL, MUTED);
        privacy.setLineSpacing(dp(3), 1f);
        privacy.setPadding(0, dp(16), 0, dp(6));
        content.addView(radiusLabel, matchWrap());
        content.addView(radius, matchWrap());
        final Switch showDestination = new Switch(this);
        showDestination.setText("在通知中显示目的地（可能在锁屏显示）");
        showDestination.setChecked(prefs.showDestinationInNotifications());
        content.addView(showDestination, matchWrap());
        content.addView(privacy, matchWrap());
        ScrollView settingsScroll = new ScrollView(this);
        settingsScroll.setVerticalScrollBarEnabled(false);
        settingsScroll.addView(content, matchWrap());
        showStyledDialog(new AlertDialog.Builder(this).setTitle("提醒设置与隐私").setView(settingsScroll)
                .setNeutralButton("清空数据", (dialog, which) -> confirmClearData())
                .setNegativeButton(getString(R.string.cancel), null)
                .setPositiveButton(getString(R.string.save), (dialog, which) -> {
                    prefs.setRadius(300 + radius.getProgress() * 100);
                    prefs.setShowDestinationInNotifications(showDestination.isChecked());
                    AppPrefs.ReminderTask task = prefs.getTask();
                    if (task != null && task.isInterrupted()) {
                        ReminderRuntimeCoordinator.showRestoreNotification(this, task, null);
                    } else if (task != null && task.isAlerting()
                            && TrackingService.getRuntimeSnapshot().state == ReminderRuntime.State.STOPPED) {
                        ArrivalNotification.refreshIfPosted(this, prefs, task);
                    }
                    refreshUi();
                }));
    }

    private void confirmClearData() {
        showStyledDialog(new AlertDialog.Builder(this).setTitle("清空本机数据？").setMessage("当前提醒、最近地点和设置都会删除。")
                .setNegativeButton(getString(R.string.cancel), null).setPositiveButton("清空", (dialog, which) -> {
                    for (AppPrefs.FixedAlarm alarm : prefs.getFixedAlarms()) ScheduleManager.cancel(MainActivity.this, alarm.id);
                    prefs.clearAll();
                    try {
                        ReminderRuntimeCoordinator.stopReminder(this);
                    } catch (RuntimeException ignored) {
                        toast("服务停止失败，但本机任务仍会清除");
                    }
                    refreshUi();
                    toast("本机数据已清空");
                }));
    }

    private void showPrivacy() {
        String message = "搜索地点时，应用可能在已授权定位的情况下读取系统保存的最近位置作为查询条件；只有在你主动开始到站提醒后才会持续使用位置，停止提醒或到站触发后立即停止定位。\n\n" +
                "地点候选默认由 Photon/OpenStreetMap 提供；若开发者构建时配置了高德服务，则优先使用高德。搜索关键词和可选的附近坐标会直接发送给地点服务，结果仅做本机会话缓存；应用没有自己的业务服务器。\n\n" +
                (AmapAndroidPlaceSearch.isAvailable()
                        ? "你可以在首页同意启用高德搜索 SDK。启用后，搜索词交给高德处理，应用不会主动发送附近坐标。你可随时切回原搜索来源。\n\n"
                        : "") +
                "导入或粘贴带经纬度的目的地时，应用会在你确认之前把该目的地坐标交给系统 Geocoder 反查地址；该系统服务是否联网及实际提供方由设备决定。\n\n" +
                "目的地、最近地点、提醒半径和设置只保存在本机。通知默认隐藏目的地；你可在提醒设置中主动开启显示，开启后系统设置可能允许锁屏显示目的地。到站详情页面需要解锁查看。应用不读取其他 App 的通知或使用记录。\n\n" +
                "强制结束应用、关闭定位、静音/勿扰或地下无信号时，闹钟可能无法触发。";
        showStyledDialog(new AlertDialog.Builder(this).setTitle(getString(R.string.privacy_title)).setMessage(message)
                .setNeutralButton("在线隐私政策", (dialog, which) -> openPrivacyPolicy())
                .setPositiveButton("知道了", null));
    }

    private void openPrivacyPolicy() {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL))); }
        catch (Exception ignored) { toast("暂时无法打开在线隐私政策"); }
    }

    private void applySafeArea(final View root) {
        final int horizontal = dimension(R.dimen.home_horizontal_padding);
        final int top = dimension(R.dimen.home_top_padding);
        final int bottom = dimension(R.dimen.home_bottom_padding);
        root.setPadding(horizontal, top, horizontal, bottom);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets windowInsets) {
                int leftInset;
                int topInset;
                int rightInset;
                int bottomInset;
                if (Build.VERSION.SDK_INT >= 30) {
                    Insets bars = windowInsets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    leftInset = bars.left;
                    topInset = bars.top;
                    rightInset = bars.right;
                    bottomInset = bars.bottom;
                } else {
                    leftInset = windowInsets.getStableInsetLeft();
                    topInset = windowInsets.getStableInsetTop();
                    rightInset = windowInsets.getStableInsetRight();
                    bottomInset = windowInsets.getStableInsetBottom();
                }
                view.setPadding(horizontal + leftInset, top + topInset, horizontal + rightInset, bottom + bottomInset);
                return windowInsets;
            }
        });
        root.post(new Runnable() {
            @Override public void run() { root.requestApplyInsets(); }
        });
    }

    private void configurePalette() {
        GREEN = getColor(R.color.deep_green);
        INK = getColor(R.color.ink);
        MUTED = getColor(R.color.muted);
        PAPER = getColor(R.color.paper);
        SURFACE = getColor(R.color.surface);
        SOFT_BLUE = getColor(R.color.soft_blue);
        SEPARATOR = getColor(R.color.separator);
        DANGER = getColor(R.color.danger);
    }

    private AlertDialog showStyledDialog(AlertDialog.Builder builder) {
        final AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(roundRect(SURFACE, 24, Color.TRANSPARENT));
            if (dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(GREEN);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setAllCaps(false);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(GREEN);
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setAllCaps(false);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEUTRAL) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(DANGER);
                dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setAllCaps(false);
            }
            TextView message = dialog.findViewById(android.R.id.message);
            if (message != null) {
                message.setTextColor(INK);
                message.setLineSpacing(dp(3), 1f);
            }
        });
        dialog.show();
        return dialog;
    }

    // 该监听只做按压缩放动画并始终返回 false，点击事件仍由 View 自身处理，
    // 因此不会影响 TalkBack 等无障碍点击，无需再调用 performClick（重复调用反而会触发两次点击）。
    @SuppressLint("ClickableViewAccessibility")
    private void applyPressFeedback(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View target, MotionEvent event) {
                if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
                    target.animate().cancel();
                    target.animate().scaleX(0.985f).scaleY(0.985f).setDuration(35).start();
                } else if (event.getActionMasked() == MotionEvent.ACTION_UP || event.getActionMasked() == MotionEvent.ACTION_CANCEL) {
                    target.animate().cancel();
                    target.animate().scaleX(1f).scaleY(1f).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
                }
                return false;
            }
        });
    }

    private void animateEntrance(final View root) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        root.setAlpha(0f);
        root.setTranslationY(dp(8));
        root.post(new Runnable() {
            @Override public void run() {
                root.animate().alpha(1f).translationY(0f).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
            }
        });
    }

    private String formatRadius(int meters) { return meters < 1000 ? meters + " 米" : (meters % 1000 == 0 ? String.valueOf(meters / 1000) : String.format(Locale.CHINA, "%.1f", meters / 1000f)) + " 公里"; }
    private String formatDistance(double meters) { return meters < 1000 ? Math.round(meters / 10) * 10 + " 米" : String.format(Locale.CHINA, meters >= 10_000 ? "%.0f 公里" : "%.1f 公里", meters / 1000); }

    private void hideKeyboard() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(destinationInput.getWindowToken(), 0); }
    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int dimension(int resource) { return getResources().getDimensionPixelSize(resource); }
    private LinearLayout column() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); return layout; }
    private LinearLayout row() { LinearLayout layout = new LinearLayout(this); layout.setOrientation(LinearLayout.HORIZONTAL); return layout; }
    private LinearLayout.LayoutParams matchWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private LinearLayout.LayoutParams wrapWrap() { return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT); }
    private TextView text(String value, int sp, int style, int color) { TextView view = new TextView(this); view.setText(value); view.setTextSize(sp); view.setTextColor(color); view.setTypeface(Typeface.create("sans", style)); return view; }
    private Button secondaryButton(String label) { return actionButton(label, 14, GREEN, SURFACE, SEPARATOR); }
    private Button actionButton(String label, int textSize, int textColor, int fill, int stroke) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(textColor);
        button.setTextSize(textSize);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setMinWidth(dimension(R.dimen.minimum_touch_target));
        button.setMinHeight(dimension(R.dimen.minimum_touch_target));
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setBackground(roundRect(fill, 14, stroke));
        button.setStateListAnimator(null);
        applyPressFeedback(button);
        return button;
    }
    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) { GradientDrawable drawable = new GradientDrawable(); drawable.setColor(fill); drawable.setCornerRadius(dp(radiusDp)); drawable.setStroke(dp(1), stroke); return drawable; }
}
