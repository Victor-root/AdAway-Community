package org.adaway.ui.welcome

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.ArrayRes
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.adaway.R
import org.adaway.data.settings.BlockingMethod
import org.adaway.ui.common.AdAwayAlertDialog
import org.adaway.ui.common.DISABLED_ALPHA
import org.adaway.ui.common.DialogButton
import org.adaway.ui.common.updateErrorTitle
import org.adaway.ui.theme.AdAwayType
import org.adaway.ui.theme.BrandRed

private val WelcomeCard = Color(0xFF424242)
private val WelcomeCardSelected = Color(0xFF388E3C)
/** Before asking for the notification permission, unless the synchronization ends first. */
private const val NOTIFICATIONS_DELAY_MILLIS = 10_000L

/** The phone first launch wizard, on the red background, a dot per step at the bottom. */
@Composable
fun WelcomeScreen(state: WelcomeState, viewModel: WelcomeViewModel, chooseVpn: () -> Unit, finish: () -> Unit) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val sync by viewModel.sync.collectAsStateWithLifecycle()
    var leaveEarly by rememberSaveable { mutableStateOf(false) }
    val last = step == WelcomeViewModel.STEP_COUNT - 1
    val next = {
        when {
            last -> finish()
            step == WelcomeViewModel.SYNC_STEP && sync != WelcomeSync.Done -> leaveEarly = true
            else -> viewModel.goTo(step + 1)
        }
    }
    BackHandler(enabled = step > 0) { viewModel.goTo(step - 1) }
    Box(modifier = Modifier.fillMaxSize().background(BrandRed).systemBarsPadding()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (step) {
                    0 -> MethodStep(state.method, chooseVpn)
                    WelcomeViewModel.SYNC_STEP -> SyncStep(sync, viewModel::startSync)
                    2 -> BatteryStep()
                    else -> OemBatteryStep()
                }
            }
            Dots(step, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        }
        if (step > 0) {
            WizardButton(R.string.welcome_back_button, { viewModel.goTo(step - 1) }, Modifier.align(Alignment.BottomStart))
        }
        if (step != 0 || state.method == BlockingMethod.VPN) {
            WizardButton(if (last) R.string.welcome_finish_button else R.string.welcome_next_button, next, Modifier.align(Alignment.BottomEnd))
        }
    }
    if (leaveEarly) {
        AdAwayAlertDialog(
            onDismissRequest = { leaveEarly = false },
            title = stringResource(R.string.welcome_sync_leave_early_title),
            message = stringResource(R.string.welcome_sync_leave_early_message),
            confirmButton = DialogButton(stringResource(R.string.welcome_sync_leave_early_continue), onClick = {
                leaveEarly = false
                viewModel.goTo(step + 1)
            }),
            dismissButton = DialogButton(stringResource(R.string.welcome_sync_leave_early_wait), onClick = { leaveEarly = false }),
        )
    }
}

/** Root blocking is not available yet: its card is grayed out. */
@Composable
private fun MethodStep(method: BlockingMethod, chooseVpn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(BrandRed)) {
            Image(
                painterResource(R.mipmap.icon_foreground),
                contentDescription = stringResource(R.string.logo_description),
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Header(R.string.welcome_method_header)
        Body(R.string.welcome_method_summary)
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MethodCard(
                icon = R.drawable.ic_superuser_24dp,
                title = R.string.welcome_root_method_title,
                points = listOf(true to R.string.welcome_root_method_text1, true to R.string.welcome_root_method_text2, false to R.string.welcome_root_method_text3),
                selected = false,
                onClick = null,
                modifier = Modifier.weight(1f).alpha(DISABLED_ALPHA),
            )
            MethodCard(
                icon = R.drawable.ic_vpn_key_24dp,
                title = R.string.welcome_vpn_method_title,
                points = listOf(false to R.string.welcome_vpn_method_text1, false to R.string.welcome_vpn_method_text2, true to R.string.welcome_vpn_method_text3),
                selected = method == BlockingMethod.VPN,
                onClick = chooseVpn,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** A method, with its advantages and drawbacks; green once chosen. */
@Composable
private fun MethodCard(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    points: List<Pair<Boolean, Int>>,
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(if (selected) WelcomeCardSelected else WelcomeCard)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp))
        Text(
            stringResource(title),
            style = AdAwayType.medium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        points.forEachIndexed { index, (advantage, text) ->
            Row(modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 10.dp else 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(if (advantage) R.drawable.ic_add_circle_outline_24dp else R.drawable.ic_remove_circle_outline_24dp),
                    contentDescription = stringResource(if (advantage) R.string.welcome_method_pro_logo else R.string.welcome_method_con_logo),
                    tint = Color.White,
                )
                Text(stringResource(text), style = AdAwayType.small, color = Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun SyncStep(sync: WelcomeSync?, retry: () -> Unit) {
    val context = LocalContext.current
    val notificationsNeeded = remember {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    }
    var notificationsAsked by rememberSaveable { mutableStateOf(false) }
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val askNotifications = {
        if (notificationsNeeded && !notificationsAsked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationsAsked = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    LaunchedEffect(Unit) {
        delay(NOTIFICATIONS_DELAY_MILLIS)
        askNotifications()
    }
    LaunchedEffect(sync) { if (sync == WelcomeSync.Done) askNotifications() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        val indicatorSize = maxHeight * 0.2f
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(modifier = Modifier.size(indicatorSize), contentAlignment = Alignment.Center) {
                when (sync) {
                    WelcomeSync.Done -> Icon(painterResource(R.drawable.baseline_check_24), null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    is WelcomeSync.Failed -> Icon(painterResource(R.drawable.ic_cloud_off_24dp), null, tint = Color.White, modifier = Modifier.fillMaxSize())
                    WelcomeSync.Running, null -> CircularProgressIndicator(modifier = Modifier.fillMaxSize(), color = Color.White)
                }
            }
            BasicText(
                stringResource(if (sync == WelcomeSync.Done) R.string.welcome_synced_header else R.string.welcome_sync_header),
                style = AdAwayType.large.copy(color = Color.White, textAlign = TextAlign.Center, fontSize = 45.sp),
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(maxFontSize = 45.sp),
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            )
            Text(
                stringResource(R.string.welcome_sync_summary),
                style = AdAwayType.large,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 32.dp),
            )
            if (sync is WelcomeSync.Failed) {
                Row(
                    modifier = Modifier.padding(top = 32.dp).clickable(onClick = retry),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_sync_24dp), stringResource(R.string.welcome_sync_retry_logo), tint = Color.White, modifier = Modifier.size(64.dp))
                    Text(
                        stringResource(R.string.welcome_sync_error, updateErrorTitle(sync.error)),
                        style = AdAwayType.large,
                        color = Color.White,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            } else if (notificationsNeeded) {
                Text(
                    stringResource(R.string.welcome_sync_notifications),
                    style = AdAwayType.medium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp).verticalScroll(rememberScrollState()),
                )
            }
        }
    }
}

// AdAway is not on the Play Store, and its VPN has to keep running in the background.
@SuppressLint("BatteryLife")
@Composable
private fun BatteryStep() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        granted = context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true
        onPauseOrDispose {}
    }
    StepColumn(R.string.welcome_battery_header, R.string.welcome_battery_summary) {
        WhiteButton(
            if (granted) R.string.welcome_battery_granted else R.string.welcome_battery_action,
            enabled = !granted,
            modifier = Modifier.padding(top = 16.dp),
        ) {
            try {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri()))
            } catch (_: ActivityNotFoundException) {
                try {
                    context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    // Nothing to open on this device.
                }
            }
        }
        Text(
            stringResource(R.string.welcome_battery_footnote_prefix),
            style = AdAwayType.small,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 16.dp),
        )
        Breadcrumb(R.array.welcome_battery_footnote_path, bold = true, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
    }
}

@Composable
private fun OemBatteryStep() {
    val context = LocalContext.current
    val brand = remember { OemBrand.detect() }
    val failure = stringResource(R.string.welcome_oem_battery_open_failed)
    StepColumn(R.string.welcome_oem_battery_header, R.string.welcome_oem_battery_summary) {
        Text(
            stringResource(R.string.welcome_oem_battery_disclaimer),
            style = AdAwayType.small,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp).alpha(0.8f),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(WelcomeCard)
                .padding(12.dp),
        ) {
            Text(stringResource(brand.label), style = AdAwayType.medium, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 6.dp))
            brand.path?.let { Breadcrumb(it, bold = false, modifier = Modifier.padding(bottom = 6.dp).alpha(0.85f), center = false) }
            Text(stringResource(brand.instruction), style = AdAwayType.medium, color = Color.White, textAlign = TextAlign.Justify)
            brand.secondary?.let {
                Text(stringResource(it), style = AdAwayType.medium, color = Color.White, textAlign = TextAlign.Justify, modifier = Modifier.padding(top = 4.dp))
            }
        }
        WhiteButton(R.string.welcome_oem_battery_action, enabled = true, modifier = Modifier.padding(top = 10.dp, bottom = 8.dp)) {
            try {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri()))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, failure, Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/** The tips for the battery manager of the phone brand, found from its manufacturer and brand names. */
private enum class OemBrand(
    @StringRes val label: Int,
    @ArrayRes val path: Int?,
    @StringRes val instruction: Int,
    @StringRes val secondary: Int?,
    val needles: List<String>,
) {
    ONE_UI(R.string.welcome_oem_battery_label_oneui, R.array.welcome_oem_battery_path_oneui, R.string.welcome_oem_battery_instruction_oneui, R.string.welcome_oem_battery_secondary_oneui, listOf("samsung")),
    MIUI(R.string.welcome_oem_battery_label_miui, R.array.welcome_oem_battery_path_miui, R.string.welcome_oem_battery_instruction_miui, R.string.welcome_oem_battery_secondary_miui, listOf("xiaomi", "redmi", "poco")),
    COLOR_OS(R.string.welcome_oem_battery_label_coloros, R.array.welcome_oem_battery_path_coloros, R.string.welcome_oem_battery_instruction_coloros, R.string.welcome_oem_battery_secondary_coloros, listOf("oppo", "oneplus", "realme")),
    EMUI(R.string.welcome_oem_battery_label_emui, R.array.welcome_oem_battery_path_emui, R.string.welcome_oem_battery_instruction_emui, R.string.welcome_oem_battery_secondary_emui, listOf("huawei", "honor")),
    FUNTOUCH(R.string.welcome_oem_battery_label_funtouch, R.array.welcome_oem_battery_path_funtouch, R.string.welcome_oem_battery_instruction_funtouch, R.string.welcome_oem_battery_secondary_funtouch, listOf("vivo", "iqoo")),
    GENERIC(R.string.welcome_oem_battery_label_generic, null, R.string.welcome_oem_battery_tip_generic, null, emptyList()),
    ;

    companion object {
        fun detect(): OemBrand {
            val names = listOf(Build.MANUFACTURER, Build.BRAND).map { it.orEmpty().lowercase() }
            return entries.firstOrNull { brand -> brand.needles.any { needle -> names.any { it.contains(needle) } } } ?: GENERIC
        }
    }
}

@Composable
private fun StepColumn(@StringRes header: Int, @StringRes summary: Int, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            painterResource(R.drawable.ic_settings_24dp),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.padding(top = 8.dp).size(44.dp),
        )
        Header(header)
        Body(summary)
        content()
    }
}

@Composable
private fun Header(@StringRes text: Int) {
    Text(
        stringResource(text),
        fontSize = 22.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

@Composable
private fun Body(@StringRes text: Int) {
    Text(
        stringResource(text),
        style = AdAwayType.medium,
        color = Color.White,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
    )
}

/** Path segments separated by small chevrons, as "Settings › Apps › AdAway". */
@Composable
private fun Breadcrumb(@ArrayRes segments: Int, bold: Boolean, modifier: Modifier = Modifier, center: Boolean = true) {
    val parts = stringArrayResource(segments)
    val text = buildAnnotatedString {
        parts.forEachIndexed { index, part ->
            if (index > 0) {
                append(" ")
                appendInlineContent(CHEVRON)
                append(" ")
            }
            append(part)
        }
    }
    val chevron = InlineTextContent(Placeholder(12.sp, 12.sp, PlaceholderVerticalAlign.Center)) {
        Icon(painterResource(R.drawable.ic_chevron_right_24dp), contentDescription = null, tint = Color.White, modifier = Modifier.fillMaxSize())
    }
    Text(
        text,
        inlineContent = mapOf(CHEVRON to chevron),
        style = AdAwayType.small,
        fontWeight = if (bold) FontWeight.Bold else null,
        color = Color.White,
        textAlign = if (center) TextAlign.Center else TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

private const val CHEVRON = "chevron"

/** White pill with red bold text on the red pages; dimmed once done. */
@Composable
private fun WhiteButton(@StringRes text: Int, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.6f)
            .clip(CircleShape)
            .background(Color.White)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(stringResource(text).uppercase(), style = AdAwayType.button, fontWeight = FontWeight.Bold, color = BrandRed)
    }
}

@Composable
private fun WizardButton(@StringRes text: Int, onClick: () -> Unit, modifier: Modifier) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(text).uppercase(), style = AdAwayType.button, color = Color.White)
    }
}

/** A dot per step, the current one filled and larger. */
@Composable
private fun Dots(step: Int, modifier: Modifier) {
    Row(modifier = modifier.height(32.dp), verticalAlignment = Alignment.CenterVertically) {
        val descriptions = listOf(R.string.welcome_first_step, R.string.welcome_second_step, R.string.welcome_third_step, R.string.welcome_fourth_step)
        descriptions.forEachIndexed { index, description ->
            val current = index == step
            val text = stringResource(description)
            Box(
                modifier = Modifier
                    .semantics { contentDescription = text }
                    .padding(start = 8.dp, end = if (index == WelcomeViewModel.STEP_COUNT - 1) 8.dp else 0.dp)
                    .size(12.dp)
                    .scale(if (current) 1.2f else 1f)
                    .alpha(if (current) 0.7f else 0.5f)
                    .then(
                        if (current) Modifier.background(Color.White, CircleShape) else Modifier.border(1.dp, Color.White, CircleShape),
                    ),
            )
        }
    }
}
