/*
 * Copyright (C) 2018 Yank555.lu Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.tuner;

import android.content.ContentResolver;
import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.support.v14.preference.PreferenceFragment;
import android.support.v14.preference.SwitchPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.Preference.OnPreferenceChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import com.android.systemui.R;
import com.android.systemui.tuner.CustomSeekBarPreference;

public class NavigationKeysDimmerFragment extends PreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String KEY_NAVIGATION_KEYS_ALWAYS_DIM =
            "navigation_keys_always_dim";
    private static final String KEY_NAVIGATION_KEYS_LIGHTSIN_ALPHA =
            "navigation_keys_lightsin_alpha";
    private static final String KEY_NAVIGATION_KEYS_LIGHTSOUT_ALPHA =
            "navigation_keys_lightsout_alpha";
    private static final String KEY_NAVIGATION_KEYS_LIGHTSIN_DURATION =
            "navigation_keys_lightsin_duration";
    private static final String KEY_NAVIGATION_KEYS_LIGHTSOUT_DURATION =
            "navigation_keys_lightsout_duration";
    private static final String KEY_NAVIGATION_KEYS_LIGHTSOUT_DELAY =
            "navigation_keys_lightsout_delay";

    private SwitchPreference        mNavButtonsAlwaysDim;
    private CustomSeekBarPreference mNavButtonsAlphaLightsIn;
    private CustomSeekBarPreference mNavButtonsAlphaLightsOut;
    private CustomSeekBarPreference mDurationLightsIn;
    private CustomSeekBarPreference mDurationLightsOut;
    private CustomSeekBarPreference mDelayLightsOut;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        addPreferencesFromResource(R.xml.navigation_keys_dimmer_settings);

        // Always dim navigation bar (also when wallpaper is visible)
        mNavButtonsAlwaysDim = (SwitchPreference) findPreference(KEY_NAVIGATION_KEYS_ALWAYS_DIM);
        mNavButtonsAlwaysDim.setOnPreferenceChangeListener(this);

        // Navigation bar lights in transparency (aka. non-dimmed)
        mNavButtonsAlphaLightsIn =
                (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_KEYS_LIGHTSIN_ALPHA);
        mNavButtonsAlphaLightsIn.setOnPreferenceChangeListener(this);

        // Navigation bar lights out transparency (aka. dimmed)
        mNavButtonsAlphaLightsOut =
                (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_KEYS_LIGHTSOUT_ALPHA);
        mNavButtonsAlphaLightsOut.setOnPreferenceChangeListener(this);

        // Navigation bar lights in animation duration (aka. dimmed -> non-dimmed)
        mDurationLightsIn =
                (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_KEYS_LIGHTSIN_DURATION);
        mDurationLightsIn.setOnPreferenceChangeListener(this);

        // Navigation bar lights out animation duration (aka. non-dimmed -> dimmed)
        mDurationLightsOut =
                (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_KEYS_LIGHTSOUT_DURATION);
        mDurationLightsOut.setOnPreferenceChangeListener(this);

        // Navigation bar lights out delay
        mDelayLightsOut =
                (CustomSeekBarPreference) findPreference(KEY_NAVIGATION_KEYS_LIGHTSOUT_DELAY);
        mDelayLightsOut.setOnPreferenceChangeListener(this);

        setHasOptionsMenu(false);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        ContentResolver mContentResolver = getContext().getContentResolver();
        // Always dim navigation bar (also when wallpaper is visible)
        if (preference == mNavButtonsAlwaysDim) {
            Boolean navButtonsAlwaysDim = (Boolean) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_ALWAYS_DIM, navButtonsAlwaysDim ? 1 : 0,
                    UserHandle.USER_CURRENT);
        }
        // Navigation bar lights in transparency (aka. non-dimmed)
        if (preference == mNavButtonsAlphaLightsIn) {
            int navButtonsAlphaLightsIn = (Integer) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_ALPHA, navButtonsAlphaLightsIn,
                    UserHandle.USER_CURRENT);
        }
        // Navigation bar lights out transparency (aka. dimmed)
        if (preference == mNavButtonsAlphaLightsOut) {
            int navButtonsAlphaLightsOut = (Integer) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_ALPHA, navButtonsAlphaLightsOut,
                    UserHandle.USER_CURRENT);
        }
        // Navigation bar lights in animation duration (aka. dimmed -> non-dimmed)
        if (preference == mDurationLightsIn) {
            int durationLightsIn = (Integer) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_DURATION, durationLightsIn,
                    UserHandle.USER_CURRENT);
        }
        // Navigation bar lights out animation duration (aka. non-dimmed -> dimmed)
        if (preference == mDurationLightsOut) {
            int durationLightsOut = (Integer) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DURATION, durationLightsOut,
                    UserHandle.USER_CURRENT);
        }
        // Navigation bar lights out delay
        if (preference == mDelayLightsOut) {
            int delayLightsOut = (Integer) newValue;
            Settings.System.putIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DELAY, delayLightsOut,
                    UserHandle.USER_CURRENT);
        }
        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        updateState();

        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, true);
    }

    @Override
    public void onPause() {
        super.onPause();

        MetricsLogger.visibility(getContext(), MetricsEvent.TUNER, false);
    }

    private void updateState() {
        ContentResolver mContentResolver = getContext().getContentResolver();
        // Always dim navigation bar (also when wallpaper is visible)
        if (mNavButtonsAlwaysDim != null) {
            int value = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_ALWAYS_DIM, 0,
                    UserHandle.USER_CURRENT);
            mNavButtonsAlwaysDim.setChecked(value != 0);
        }
        // Navigation bar lights in transparency (aka. non-dimmed)
        if (mNavButtonsAlphaLightsIn != null) {
            int navButtonsAlphaLightsIn = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_ALPHA, 100,
                    UserHandle.USER_CURRENT);
            mNavButtonsAlphaLightsIn.setValue(navButtonsAlphaLightsIn);
        }
        // Navigation bar lights out transparency (aka. dimmed)
        if (mNavButtonsAlphaLightsOut != null) {
            int navButtonsAlphaLightsOut = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_ALPHA, 60,
                    UserHandle.USER_CURRENT);
            mNavButtonsAlphaLightsOut.setValue(navButtonsAlphaLightsOut);
        }
        // Navigation bar lights in animation duration (aka. dimmed -> non-dimmed)
        if (mDurationLightsIn != null) {
            int durationLightsIn = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSIN_DURATION, 250,
                    UserHandle.USER_CURRENT);
            mDurationLightsIn.setValue(durationLightsIn);
        }
        // Navigation bar lights out animation duration (aka. non-dimmed -> dimmed)
        if (mDurationLightsOut != null) {
            int durationLightsOut = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DURATION, 1500,
                    UserHandle.USER_CURRENT);
            mDurationLightsOut.setValue(durationLightsOut);
        }
        // Navigation bar lights out delay
        if (mDelayLightsOut != null) {
            int delayLightsOut = Settings.System.getIntForUser(mContentResolver,
                    Settings.System.NAVIGATION_KEYS_LIGHTSOUT_DELAY, 2000,
                    UserHandle.USER_CURRENT);
            mDelayLightsOut.setValue(delayLightsOut);
        }
    }
}
