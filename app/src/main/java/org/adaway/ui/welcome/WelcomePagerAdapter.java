package org.adaway.ui.welcome;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/**
 * This class is a pager adapter to create setup step fragments.
 *
 * @author Bruce BUJON (bruce.bujon(at)gmail(dot)com)
 */
public class WelcomePagerAdapter extends FragmentStateAdapter {
    private static final int BATTERY_STEPS_POSITION = 2;
    private static final int BATTERY_STEPS_COUNT = 2;
    private final WelcomeMethodFragment welcomeMethodFragment;
    private final WelcomeSyncFragment welcomeSyncFragment;
    private final WelcomeBatteryFragment welcomeBatteryFragment;
    private final WelcomeOemBatteryFragment welcomeOemBatteryFragment;
    /**
     * Whether the battery steps are shown: they only matter to the VPN, which runs in the
     * background, whereas root mode only installs the hosts file.
     */
    private boolean batterySteps;

    WelcomePagerAdapter(@NonNull FragmentActivity fragmentManager) {
        super(fragmentManager);
        this.welcomeMethodFragment = new WelcomeMethodFragment();
        this.welcomeSyncFragment = new WelcomeSyncFragment();
        this.welcomeBatteryFragment = new WelcomeBatteryFragment();
        this.welcomeOemBatteryFragment = new WelcomeOemBatteryFragment();
        this.batterySteps = true;
    }

    /**
     * Show or hide the battery steps.
     *
     * @param batterySteps {@code true} to show the battery steps, {@code false} to hide them.
     */
    void setBatterySteps(boolean batterySteps) {
        if (this.batterySteps == batterySteps) {
            return;
        }
        this.batterySteps = batterySteps;
        if (batterySteps) {
            notifyItemRangeInserted(BATTERY_STEPS_POSITION, BATTERY_STEPS_COUNT);
        } else {
            notifyItemRangeRemoved(BATTERY_STEPS_POSITION, BATTERY_STEPS_COUNT);
        }
    }

    @NonNull
    @Override
    public WelcomeFragment createFragment(int position) {
        switch (position) {
            case 0:
                return this.welcomeMethodFragment;
            case 1:
                return this.welcomeSyncFragment;
            case 2:
                return this.welcomeBatteryFragment;
            case 3:
                return this.welcomeOemBatteryFragment;
            default:
                throw new IllegalStateException("Position " + position + " is not supported.");
        }
    }

    @Override
    public int getItemCount() {
        return this.batterySteps ? BATTERY_STEPS_POSITION + BATTERY_STEPS_COUNT : BATTERY_STEPS_POSITION;
    }
}
