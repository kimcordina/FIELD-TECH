# Stage 2.8: Quick Reference Guide

## What Changed?

### Reports Screen
- **Before**: Local/Team toggle with separate lists
- **After**: Single unified list showing all reports

### Visual Changes
- **Pending Reports**: Show "Pending upload" badge, slightly dimmed
- **Synced Reports**: Normal appearance, "Open PDF" button enabled

### Settings Screen
- **New**: Sync Status card showing pending upload count
- **Feature**: "Retry all" button to manually trigger sync

## Key Files

### New Files
1. `OutboxRepository.kt` - Manages pending uploads
2. `UnifiedReportsViewModel.kt` - Combines cloud + local reports

### Modified Files
1. `ReportRepository.kt` - Added `observeLocalPendingReports()`
2. `SavedReportsScreen.kt` - Unified view implementation
3. `SettingsScreen.kt` - Added Sync Status card

## How It Works

### Pending Detection
Reports are "pending" if:
- Created in last 14 days
- Have local PDF generated
- Not yet in cloud reports list

### Sync Status
- Real-time count of pending uploads
- Updates automatically via Flow
- Manual retry available

## Logging

Watch these tags in logcat:
```bash
adb logcat | grep "FT/REPORTS/UNIFIED\|FT/OUTBOX"
```

**Example Output**:
```
D/FT/REPORTS/UNIFIED: Cloud reports received: 12
D/FT/REPORTS/UNIFIED: Local pending reports: 3
D/FT/REPORTS/UNIFIED: Unified list: merged=15
D/FT/OUTBOX: Pending count: 3
```

## Testing Quick Checklist

### Basic Flow
1. ✅ Open Reports - see unified list
2. ✅ Create report - appears as pending
3. ✅ Open Settings - see pending count
4. ✅ Wait for sync - pending becomes synced

### Pending Reports
- ✅ Shows "Pending upload" badge
- ✅ Dimmed appearance (85% opacity)
- ✅ "Retry" button instead of "Open PDF"

### Synced Reports
- ✅ No badge
- ✅ Full opacity
- ✅ "Open PDF" works

### Sync Status Card
- ✅ Shows correct pending count
- ✅ "Retry all" button appears when pending > 0
- ✅ Updates in real-time

## Troubleshooting

### Pending reports not showing?
- Check if created in last 14 days
- Check if PDF was generated locally
- Check logs: `adb logcat | grep FT/REPORTS/UNIFIED`

### Wrong pending count?
- Check logs: `adb logcat | grep FT/OUTBOX`
- Verify reports have local PDFs
- Check 14-day window

### Retry not working?
- Currently a placeholder (logs action)
- Future: Will trigger WorkManager job
- Check logs for "kickNow() called"

## Build Info

**Status**: ✅ BUILD SUCCESSFUL
**APK**: `~/Downloads/MyApks/FieldTech_Debug_[timestamp].apk`
**Size**: ~118 MB

## Install & Test

```bash
# Install latest APK
adb install -r ~/Downloads/MyApks/FieldTech_Debug_*.apk

# Watch logs
adb logcat | grep "FT/"

# Clear app data (if needed)
adb shell pm clear com.ncordina.fieldtech2
```

## Known Limitations

1. **14-Day Window**: Older reports won't show as pending
2. **No WorkManager**: Retry is placeholder only
3. **Heuristic Detection**: Not explicit sync status tracking
4. **No Progress**: Can't see upload progress percentage

## Future Enhancements

### Phase 1: WorkManager
- Actual upload worker
- Periodic sync attempts
- Network constraints

### Phase 2: Enhanced Status
- Explicit sync status field
- Track failures and errors
- Show detailed status

### Phase 3: Progress
- Upload progress percentage
- Time remaining estimates
- Batch optimization

## Company Configuration

**Company ID**: `NCORDINA` (unchanged)
**Firestore Path**: `companies/NCORDINA/`
**Storage Path**: `companies/NCORDINA/reports/`

## No Breaking Changes

✅ No database schema changes
✅ No migration required
✅ Existing reports work as-is
✅ Backward compatible

## Summary

Stage 2.8 provides a cleaner, more intuitive reports view by:
- Combining cloud and local reports in one list
- Clearly marking pending uploads
- Showing sync status in Settings
- Offering manual retry mechanism

All while maintaining full backward compatibility! 🎉




