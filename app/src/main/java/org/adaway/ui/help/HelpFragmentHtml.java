/*
 * Copyright (C) 2011-2012 Dominik Schürmann <dominik@dominikschuermann.de>
 *
 * This file is part of AdAway.
 *
 * AdAway is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AdAway is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AdAway.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package org.adaway.ui.help;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.RawRes;
import androidx.fragment.app.Fragment;

import org.adaway.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import static android.text.Html.FROM_HTML_MODE_LEGACY;

import timber.log.Timber;

public class HelpFragmentHtml extends Fragment {
    private static final String ARG_HTML_FILE = "htmlFile";

    /**
     * Create a new instance of HelpFragmentHtml, providing "htmlFile" as an argument.
     */
    static HelpFragmentHtml newInstance(@RawRes int htmlFile) {
        HelpFragmentHtml instance = new HelpFragmentHtml();

        // Supply html raw file input as an argument.
        Bundle args = new Bundle();
        args.putInt(ARG_HTML_FILE, htmlFile);
        instance.setArguments(args);

        return instance;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Spanned spanned = new SpannableString("");
        if (getArguments() != null) {
            int htmlFile = getArguments().getInt(ARG_HTML_FILE);
            try {
                spanned = Html.fromHtml(readHtmlRawFile(htmlFile), FROM_HTML_MODE_LEGACY);
            } catch (IOException e) {
                Timber.w("Failed to read help file.");
            }
        }

        View view = inflater.inflate(R.layout.help_fragment, container, false);
        TextView helpTextView = view.findViewById(R.id.helpTextView);
        helpTextView.setText(spanned);
        if (isTv()) {
            // LinkMovementMethod (and textIsSelectable from the layout) makes this
            // TextView focusable and lets D-pad up/down move between the HTML's
            // <a> links instead of scrolling the surrounding ScrollView, and there
            // is no D-pad-friendly way to open a link here anyway, so on TV this
            // is a plain scrollable read, not an interactive page.
            helpTextView.setFocusable(false);
            helpTextView.setFocusableInTouchMode(false);
            // This screen's tab strip (HelpActivity) sends focus down into this
            // ScrollView on D-pad down; mirror that back once scrolled to the top,
            // instead of leaving up to fall through to default focus search.
            view.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN
                        && !view.canScrollVertically(-1)) {
                    HelpActivity.focusSelectedTab(requireActivity());
                    return true;
                }
                return false;
            });
        } else {
            helpTextView.setMovementMethod(LinkMovementMethod.getInstance());
        }
        return view;
    }

    private boolean isTv() {
        UiModeManager uiModeManager = (UiModeManager) requireContext().getSystemService(Context.UI_MODE_SERVICE);
        return uiModeManager != null && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION;
    }

    private String readHtmlRawFile(@RawRes int resourceId) throws IOException {
        try (InputStream inputStream = getResources().openRawResource(resourceId);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line);
            }
            return content.toString();
        }
    }
}
