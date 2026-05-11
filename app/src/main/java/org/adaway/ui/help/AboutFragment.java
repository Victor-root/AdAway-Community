package org.adaway.ui.help;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.adaway.BuildConfig;
import org.adaway.R;

public class AboutFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.about_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        TextView versionTextView = view.findViewById(R.id.about_version);
        versionTextView.setText(getString(R.string.about_version, BuildConfig.VERSION_NAME));

        setLink(view, R.id.about_source_link, "https://github.com/Victor-root/AdAway-Community");
        setLink(view, R.id.about_issues_link, "https://github.com/Victor-root/AdAway-Community/issues");
        setLink(view, R.id.about_releases_link, "https://github.com/Victor-root/AdAway-Community/releases");
        setLink(view, R.id.about_upstream_link, "https://github.com/AdAway/AdAway");
    }

    private void setLink(View parent, int viewId, String url) {
        parent.findViewById(viewId).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        );
    }
}
