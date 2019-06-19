/*
 * Copyright (C) 2018 Yaroslav Pronin <proninyaroslav@mail.ru>
 *
 * This file is part of LibreTorrent.
 *
 * LibreTorrent is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * LibreTorrent is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with LibreTorrent.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.proninyaroslav.libretorrent.fragments;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.Nullable;
import android.support.design.widget.CoordinatorLayout;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.design.widget.TextInputEditText;
import android.support.design.widget.TextInputLayout;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;

import com.github.aakira.expandablelayout.ExpandableLinearLayout;

import org.apache.commons.io.FileUtils;
import org.proninyaroslav.libretorrent.FeedActivity;
import org.proninyaroslav.libretorrent.FeedItemsActivity;
import org.proninyaroslav.libretorrent.R;
import org.proninyaroslav.libretorrent.adapters.FeedChannelListAdapter;
import org.proninyaroslav.libretorrent.core.FeedChannel;
import org.proninyaroslav.libretorrent.core.storage.FeedStorage;
import org.proninyaroslav.libretorrent.core.utils.FileIOUtils;
import org.proninyaroslav.libretorrent.core.utils.Utils;
import org.proninyaroslav.libretorrent.customviews.EmptyRecyclerView;
import org.proninyaroslav.libretorrent.customviews.RecyclerViewDividerDecoration;
import org.proninyaroslav.libretorrent.dialogs.AddRssChannelDialog;
import org.proninyaroslav.libretorrent.dialogs.BaseAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.ErrorReportAlertDialog;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerConfig;
import org.proninyaroslav.libretorrent.dialogs.filemanager.FileManagerDialog;
import org.proninyaroslav.libretorrent.services.FeedFetcherService;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FeedFragment extends Fragment
        implements BaseAlertDialog.OnClickListener,
                   BaseAlertDialog.OnDialogShowListener
{
    @SuppressWarnings("unused")
    private static final String TAG = FeedFragment.class.getSimpleName();

    private static final String TAG_ADD_CHANNEL_DIALOG = "add_chanel_dialog";
    private static final String TAG_ERROR_ADD_CHANNEL_DIALOG = "error_add_channel_dialog";
    private static final String TAG_DELETE_CHANNELS_DIALOG = "delete_channels_dialog";
    private static final String TAG_EDIT_CHANNEL_DIALOG = "edit_chanel_dialog";
    private static final String TAG_IMPORT_ERROR_DIALOG = "import_error_dialog";
    private static final String TAG_EXPORT_ERROR_DIALOG = "import_export_dialog";
    private static final String TAG_CHANNEL_LIST_STATE = "channel_list_state";
    private static final String TAG_SELECTABLE_ADAPTER = "selectable_adapter";
    private static final String TAG_SELECTED_CHANNELS = "selected_channels";
    private static final String TAG_IN_ACTION_MODE = "in_action_mode";
    private static final String TAG_PREV_IMPL_INTENT = "prev_impl_intent";

    private static final int IMPORT_FEEDS_CHOOSE_REQUEST = 1;
    private static final int EXPORT_FEEDS_CHOOSE_REQUEST = 2;

    private AppCompatActivity activity;
    private Toolbar toolbar;
    private FeedChannelListAdapter adapter;
    private LinearLayoutManager layoutManager;
    private EmptyRecyclerView channelList;
    private SwipeRefreshLayout swipeRefreshLayout;
    /* Save state scrolling */
    private Parcelable channelListState;
    private ArrayList<FeedChannel> selectedChannels = new ArrayList<>();
    private boolean inActionMode = false;
    private ActionMode actionMode;
    private ActionModeCallback actionModeCallback = new ActionModeCallback();
    private FloatingActionButton addChannelButton;
    private FeedStorage storage;
    private ArrayList<FeedChannel> channels = new ArrayList<>();
    private CoordinatorLayout coordinatorLayout;
    /* Prevents re-adding channel, obtained through implicit intent */
    private Intent prevImplIntent;
    private Exception sentError;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.fragment_feed, container, false);
        coordinatorLayout = v.findViewById(R.id.feed_coordinator_layout);
        toolbar = v.findViewById(R.id.toolbar);
        addChannelButton = v.findViewById(R.id.add_channel_button);
        swipeRefreshLayout = v.findViewById(R.id.swipe_container);

        return v;
    }

    @Override
    public void onAttach(Context context)
    {
        super.onAttach(context);

        if (context instanceof AppCompatActivity)
            activity = (AppCompatActivity)context;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);

        if (activity == null)
            activity = (AppCompatActivity) getActivity();

        showBlankFragment();

        if (toolbar != null) {
            toolbar.setTitle(R.string.feed);
            activity.setSupportActionBar(toolbar);

            setHasOptionsMenu(true);
            toolbar.setNavigationOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View view)
                {
                    onBackPressed();
                }
            });
            toolbar.getNavigationIcon();
            toolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item)
                {
                    return onOptionsItemSelected(item);
                }
            });
        }
        if (activity.getSupportActionBar() != null)
            activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        addChannelButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                addChannelDialog();
            }
        });

        if (savedInstanceState != null)
            prevImplIntent = savedInstanceState.getParcelable(TAG_PREV_IMPL_INTENT);

        channelList = activity.findViewById(R.id.feed_channel_list);
        layoutManager = new LinearLayoutManager(activity);
        channelList.setLayoutManager(layoutManager);
        /*
         * A RecyclerView by default creates another copy of the ViewHolder in order to
         * fade the views into each other. This causes the problem because the old ViewHolder gets
         * the payload but then the new one doesn't. So needs to explicitly tell it to reuse the old one.
         */
        DefaultItemAnimator animator = new DefaultItemAnimator()
        {
            @Override
            public boolean canReuseUpdatedViewHolder(RecyclerView.ViewHolder viewHolder)
            {
                return true;
            }
        };
        TypedArray a = activity.obtainStyledAttributes(new TypedValue().data, new int[]{ R.attr.divider });
        channelList.setItemAnimator(animator);
        channelList.addItemDecoration(new RecyclerViewDividerDecoration(a.getDrawable(0)));
        channelList.setEmptyView(activity.findViewById(R.id.empty_view_feed_channel));
        a.recycle();

        storage = new FeedStorage(activity.getApplicationContext());
        adapter = new FeedChannelListAdapter(new ArrayList<>(channels), activity,
                R.layout.item_feed_channel_list, torrentListListener);
        channelList.setAdapter(adapter);

        swipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.accent));
        swipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh()
            {
                refreshChannels(channels);
            }
        });

        Intent i = activity.getIntent();
        /* If add channel dialog has been called by an implicit intent */
        if (i != null && i.getData() != null) {
            if (prevImplIntent == null || !prevImplIntent.equals(i)) {
                prevImplIntent = i;
                Uri uri = i.getData();
                if (uri != null)
                    addChannel(Utils.normalizeURL(uri.toString()), null, null, false, false);
                else
                    addChannelErrorDialog();
            }

        } else if (i != null && i.getAction() != null &&
                   i.getAction().equals(FeedActivity.ACTION_ADD_CHANNEL_SHORTCUT)) {
            /* Prevents re-reading action after device configuration changes */
            i.setAction(null);
            addChannelDialog();
        }

        if (savedInstanceState != null) {
            selectedChannels = savedInstanceState.getParcelableArrayList(TAG_SELECTED_CHANNELS);
            if (savedInstanceState.getBoolean(TAG_IN_ACTION_MODE, false)) {
                actionMode = activity.startActionMode(actionModeCallback);
                adapter.setSelectedItems(savedInstanceState.getIntegerArrayList(TAG_SELECTABLE_ADAPTER));
                actionMode.setTitle(String.valueOf(adapter.getSelectedItemCount()));
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState)
    {
        outState.putParcelable(TAG_PREV_IMPL_INTENT, prevImplIntent);
        if (layoutManager != null) {
            channelListState = layoutManager.onSaveInstanceState();
        }
        outState.putParcelable(TAG_CHANNEL_LIST_STATE, channelListState);
        if (adapter != null) {
            outState.putIntegerArrayList(TAG_SELECTABLE_ADAPTER, adapter.getSelectedItems());
        }
        outState.putBoolean(TAG_IN_ACTION_MODE, inActionMode);
        outState.putParcelableArrayList(TAG_SELECTED_CHANNELS, selectedChannels);

        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewStateRestored(Bundle savedInstanceState)
    {
        super.onViewStateRestored(savedInstanceState);

        if (savedInstanceState != null)
            channelListState = savedInstanceState.getParcelable(TAG_CHANNEL_LIST_STATE);
    }

    @Override
    public void onResume()
    {
        super.onResume();

        channels.clear();
        channels.addAll(storage.getAllChannels());
        if (adapter != null)
            adapter.setItems(channels);
        if (channelListState != null && layoutManager != null)
            layoutManager.onRestoreInstanceState(channelListState);
    }

    @Override
    public void onStart()
    {
        super.onStart();

        LocalBroadcastManager.getInstance(activity).registerReceiver(
                feedManagerReceiver, new IntentFilter(FeedFetcherService.ACTION_CHANNEL_RESULT));
    }

    @Override
    public void onStop()
    {
        super.onStop();

        LocalBroadcastManager.getInstance(activity).unregisterReceiver(feedManagerReceiver);
    }

    private void deleteChannel(FeedChannel channel)
    {
        storage.deleteChannel(channel);
        channels.remove(channel);
        adapter.deleteItem(channel);
    }

    private boolean addChannel(FeedChannel channel)
    {
        if (storage.addChannel(channel)) {
            channels.add(channel);
            adapter.addItem(channel);

            return true;
        }

        return false;
    }

    private void updateChannelUi(FeedChannel channel)
    {
        int index = channels.indexOf(channel);
        if (index >= 0)
            channels.set(index, channel);
        adapter.updateItem(channel);
    }

    private void refreshChannels(List<FeedChannel> channels)
    {
        swipeRefreshLayout.setRefreshing(true);

        ArrayList<String> urls = new ArrayList<>();
        for (FeedChannel channel : channels)
            urls.add(channel.getUrl());

        if (urls.isEmpty())
            return;

        Intent i = new Intent(activity, FeedFetcherService.class);
        i.setAction(FeedFetcherService.ACTION_FETCH_CHANNEL_LIST);
        i.putExtra(FeedFetcherService.TAG_CHANNEL_URL_LIST_ARG, urls);
        FeedFetcherService.enqueueWork(activity, i);
    }

    private void refreshChannel(FeedChannel channel)
    {
        Intent i = new Intent(activity, FeedFetcherService.class);
        i.setAction(FeedFetcherService.ACTION_FETCH_CHANNEL);
        i.putExtra(FeedFetcherService.TAG_CHANNEL_URL_ARG, channel.getUrl());
        FeedFetcherService.enqueueWork(activity, i);
    }

    /*
     * Uncheck current feed from the list and set blank fragment.
     */

    public void resetCurOpenFeed()
    {
        adapter.markAsOpen(null);
        showBlankFragment();
    }

    private boolean checkUrlField(String s, TextInputLayout layout)
    {
        if (s == null || layout == null)
            return false;

        if (TextUtils.isEmpty(s)) {
            layout.setErrorEnabled(true);
            layout.setError(getString(R.string.error_empty_link));
            layout.requestFocus();

            return false;
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    private boolean checkFilterField(String s, boolean isRegex, TextInputLayout layout)
    {
        if (s == null || layout == null || TextUtils.isEmpty(s))
            return true;

        if (isRegex) {
            try {
                Pattern.compile(s);
            } catch (PatternSyntaxException e) {
                layout.setErrorEnabled(true);
                layout.setError(e.getMessage());
                layout.requestFocus();

                return false;
            }
        }

        layout.setErrorEnabled(false);
        layout.setError(null);

        return true;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    public void onShow(final AlertDialog dialog)
    {
        if (dialog == null)
            return;

        if (getFragmentManager().findFragmentByTag(TAG_ADD_CHANNEL_DIALOG) != null ||
            getFragmentManager().findFragmentByTag(TAG_EDIT_CHANNEL_DIALOG) != null) {
            final TextInputEditText urlField = dialog.findViewById(R.id.feed_channel_url);
            final TextInputLayout urlFieldLayout = dialog.findViewById(R.id.layout_feed_channel_url);
            final TextInputEditText filterField = dialog.findViewById(R.id.feed_channel_filter);
            final TextInputLayout filterFieldLayout = dialog.findViewById(R.id.layout_feed_channel_filter);
            final CheckBox isRegexFilter = dialog.findViewById(R.id.feed_use_regex);
            final CheckBox autoDownloadField = dialog.findViewById(R.id.feed_auto_download);
            final TextInputEditText nameField = dialog.findViewById(R.id.feed_channel_name);
            final ExpandableLinearLayout expandableLayout = dialog.findViewById(R.id.expandable_layout);

            final FeedChannel channel = (selectedChannels.size() != 0 ? selectedChannels.get(0) : null);
            if (getFragmentManager().findFragmentByTag(TAG_EDIT_CHANNEL_DIALOG) != null) {
                if (channel != null) {
                    urlField.setText(channel.getUrl());
                    nameField.setText(channel.getName());
                    filterField.setText(channel.getFilter());
                    filterField.setEnabled(channel.getFilter() != null && TextUtils.isEmpty(channel.getFilter()));
                    isRegexFilter.setChecked(channel.isRegexFilter());
                    isRegexFilter.setEnabled(channel.isRegexFilter());
                    autoDownloadField.setChecked(channel.isAutoDownload());
                }
                selectedChannels.clear();
            }

            /* Dismiss error label if user has changed the text */
            urlField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count)
                {
                    urlFieldLayout.setErrorEnabled(false);
                    urlFieldLayout.setError(null);
                }

                @Override
                public void afterTextChanged(Editable s) { /* Nothing */ }
            });
            filterField.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) { /* Nothing */ }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count)
                {
                    filterFieldLayout.setErrorEnabled(false);
                    filterFieldLayout.setError(null);
                }

                @Override
                public void afterTextChanged(Editable s) { /* Nothing */ }
            });
            filterField.setOnTouchListener(new View.OnTouchListener()
            {
                @Override
                public boolean onTouch(View v, MotionEvent event)
                {
                    final int DRAWABLE_LEFT = 0;
                    final int DRAWABLE_TOP = 1;
                    final int DRAWABLE_RIGHT = 2;
                    final int DRAWABLE_BOTTOM = 3;

                    if(event.getAction() == MotionEvent.ACTION_UP) {
                        if (event.getRawX() >= (filterField.getRight() -
                                filterField.getCompoundDrawables()[DRAWABLE_RIGHT].getBounds().width())) {
                            expandableLayout.toggle();

                            return true;
                        }
                    }

                    return false;
                }
            });

            /*
             * It is necessary in order to the dialog is not closed by
             * pressing positive button if the text checker gave a false result
             */

            Button positiveButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positiveButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    String link = urlField.getText().toString();
                    String filter = filterField.getText().toString();
                    String name = nameField.getText().toString();
                    boolean isRegex = isRegexFilter.isChecked();
                    boolean autoDownload = autoDownloadField.isChecked();

                    if (checkUrlField(link, urlFieldLayout) &&
                        checkFilterField(filter, isRegex, filterFieldLayout)) {
                        /* Delete old channel after edit */
                        if (channel != null)
                            deleteChannel(channel);
                        addChannel(Utils.normalizeURL(link), name, filter, isRegex, autoDownload);
                        dialog.dismiss();
                    }
                }
            });

            /* Inserting a link from the clipboard */
            String clipboard = Utils.getClipboard(activity.getApplicationContext());
            String url = null;
            if (clipboard != null) {
                if (clipboard.startsWith(Utils.HTTP_PREFIX) ||
                    clipboard.startsWith(Utils.HTTPS_PREFIX)) {
                    url = clipboard;
                }
                if (url != null)
                    urlField.setText(url);
            }
        }
    }

    private void addChannel(String url, String name, String filter,
                            boolean isRegex, boolean autoDownload)
    {
        FeedChannel channel = new FeedChannel(url, name, 0, autoDownload, filter, isRegex, null);
        if (addChannel(channel))
            refreshChannel(channel);
        else
            addChannelErrorDialog();
    }

    BroadcastReceiver feedManagerReceiver = new BroadcastReceiver()
    {
        @Override
        public void onReceive(Context context, Intent i)
        {
            if (i == null || i.getAction() == null)
                return;

            if (i.getAction().equals(FeedFetcherService.ACTION_CHANNEL_RESULT)) {
                String url = i.getStringExtra(FeedFetcherService.TAG_CHANNEL_URL_RESULT);
                if (url == null)
                    return;
                FeedChannel channel = storage.getChannelByUrl(url);
                if (channel == null)
                    return;
                updateChannelUi(channel);
                swipeRefreshLayout.setRefreshing(false);
            }
        }
    };

    @Override
    public void onPositiveClicked(@Nullable View v)
    {
        if (getFragmentManager().findFragmentByTag(TAG_DELETE_CHANNELS_DIALOG) != null) {
            FeedItemsFragment f = getCurrentFeedItemsFragment();
            if (f != null) {
                String feedUrl = f.getFeedUrl();
                if (selectedChannels.contains(new FeedChannel(feedUrl)))
                    resetCurOpenFeed();
            }
            for (FeedChannel channel : selectedChannels)
                deleteChannel(channel);
            selectedChannels.clear();

        } else if (getFragmentManager().findFragmentByTag(TAG_IMPORT_ERROR_DIALOG) != null ||
                   getFragmentManager().findFragmentByTag(TAG_EXPORT_ERROR_DIALOG) != null) {
            if (sentError != null) {
                String comment = null;
                if (v != null) {
                    EditText editText = v.findViewById(R.id.comment);
                    comment = editText.getText().toString();
                }
                Utils.reportError(sentError, comment);
            }
        }
    }

    @Override
    public void onNegativeClicked(@Nullable View v)
    {
        selectedChannels.clear();
    }

    @Override
    public void onNeutralClicked(@Nullable View v)
    {
        /* Nothing */
    }

    private void onItemSelected(FeedChannel channel, int position)
    {
        toggleSelection(position);

        if (selectedChannels.contains(channel))
            selectedChannels.remove(channel);
        else
            selectedChannels.add(channel);

        int size = selectedChannels.size();
        /* Show/hide menu items after change selected channels */
        if (actionMode != null && (size == 1 || size == 2))
            actionMode.invalidate();
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        int count = adapter.getSelectedItemCount();

        if (count == 0) {
            actionMode.finish();
        } else {
            actionMode.setTitle(String.valueOf(count));
            actionMode.invalidate();
        }
    }

    FeedChannelListAdapter.ViewHolder.ClickListener torrentListListener = new FeedChannelListAdapter.ViewHolder.ClickListener()
    {
        @Override
        public void onItemClicked(int position, FeedChannel item)
        {
            if (actionMode == null) {
                /* Mark this channel as open in the list */
                adapter.markAsOpen(item);
                showFeedItems(item);
            } else {
                onItemSelected(item, position);
            }
        }

        @Override
        public boolean onItemLongClicked(int position, FeedChannel item)
        {
            if (actionMode == null)
                actionMode = activity.startActionMode(actionModeCallback);
            onItemSelected(item, position);

            return true;
        }
    };

    private void showBlankFragment()
    {
        if (Utils.isLargeScreenDevice(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();
            BlankFragment blank = BlankFragment.newInstance(getString(R.string.select_or_add_feed_channel));

            fm.beginTransaction()
                    .replace(R.id.feed_items_fragmentContainer, blank)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
                    .commitAllowingStateLoss();
        }
    }

    private void showFeedItems(FeedChannel channel)
    {
        if (channel == null)
            return;

        if (Utils.isTwoPane(activity.getApplicationContext())) {
            FragmentManager fm = getFragmentManager();
            FeedItemsFragment feedItems = FeedItemsFragment.newInstance(channel.getUrl());
            Fragment fragment = fm.findFragmentById(R.id.detail_torrent_fragmentContainer);
            if (fragment != null && fragment instanceof FeedItemsFragment) {
                String oldFeedUrl = ((FeedItemsFragment)fragment).getFeedUrl();
                if (oldFeedUrl != null && channel.getUrl().equals(oldFeedUrl))
                    return;
            }

            fm.beginTransaction()
                    .replace(R.id.feed_items_fragmentContainer, feedItems)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .commit();

        } else {
            Intent i = new Intent(activity, FeedItemsActivity.class);
            i.putExtra(FeedItemsActivity.TAG_FEED_URL, channel.getUrl());
            startActivity(i);
        }
    }

    public FeedItemsFragment getCurrentFeedItemsFragment()
    {
        if (!Utils.isTwoPane(activity.getApplicationContext()))
            return null;

        Fragment fragment = getFragmentManager()
                .findFragmentById(R.id.feed_items_fragmentContainer);

        return (fragment instanceof FeedItemsFragment ? (FeedItemsFragment)fragment : null);
    }

    private void addChannelDialog()
    {
        if (getFragmentManager().findFragmentByTag(TAG_ADD_CHANNEL_DIALOG) == null) {
            AddRssChannelDialog addDialog = AddRssChannelDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.add_feed_channel),
                    getString(R.string.ok),
                    FeedFragment.this);
            addDialog.show(getFragmentManager(), TAG_ADD_CHANNEL_DIALOG);
        }
    }

    private void addChannelErrorDialog()
    {
        if (getFragmentManager().findFragmentByTag(TAG_ERROR_ADD_CHANNEL_DIALOG) == null) {
            BaseAlertDialog addDialog = BaseAlertDialog.newInstance(
                    getString(R.string.error),
                    getString(R.string.error_cannot_add_channel),
                    0,
                    getString(R.string.ok),
                    null,
                    null,
                    this);
            addDialog.show(getFragmentManager(), TAG_ERROR_ADD_CHANNEL_DIALOG);
        }
    }

    private void exportChooseDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        FileManagerConfig config = new FileManagerConfig(FileIOUtils.getUserDirPath(),
                null,
                null,
                FileManagerConfig.DIR_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, EXPORT_FEEDS_CHOOSE_REQUEST);
    }

    private void importChooseDialog()
    {
        Intent i = new Intent(activity, FileManagerDialog.class);

        List<String> fileType = new ArrayList<>();
        fileType.add(FeedStorage.SERIALIZE_FILE_FORMAT);
        FileManagerConfig config = new FileManagerConfig(FileIOUtils.getUserDirPath(),
                getString(R.string.feeds_file_selection_dialog_title),
                fileType,
                FileManagerConfig.FILE_CHOOSER_MODE);

        i.putExtra(FileManagerDialog.TAG_CONFIG, config);

        startActivityForResult(i, IMPORT_FEEDS_CHOOSE_REQUEST);
    }

    private void importErrorDialog(Exception e)
    {
        sentError = e;

        if (getFragmentManager().findFragmentByTag(TAG_IMPORT_ERROR_DIALOG) == null) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_import_feeds),
                    (e != null ? Log.getStackTraceString(e) : null),
                    this);

            errDialog.show(getFragmentManager(), TAG_IMPORT_ERROR_DIALOG);
        }
    }

    private void exportErrorDialog(Exception e)
    {
        sentError = e;

        if (getFragmentManager().findFragmentByTag(TAG_EXPORT_ERROR_DIALOG) == null) {
            ErrorReportAlertDialog errDialog = ErrorReportAlertDialog.newInstance(
                    activity.getApplicationContext(),
                    getString(R.string.error),
                    getString(R.string.error_export_feeds),
                    (e != null ? Log.getStackTraceString(e) : null),
                    this);

            errDialog.show(getFragmentManager(), TAG_EXPORT_ERROR_DIALOG);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
    {
        super.onCreateOptionsMenu(menu, inflater);

        if (!isAdded())
            return;

        inflater.inflate(R.menu.feed, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        int i = item.getItemId();
        if (i == android.R.id.home) {
            onBackPressed();
        } else if (i == R.id.refresh_feed_channel_menu) {
            refreshChannels(channels);
        } else if (i == R.id.export_feed_channels_menu) {
            exportChooseDialog();
        } else if (i == R.id.import_feed_channels_menu) {
            importChooseDialog();
        }

        return true;
    }

    private class ActionModeCallback implements ActionMode.Callback
    {
        @Override
        public boolean onPrepareActionMode(ActionMode mode, Menu menu)
        {
            MenuItem edit = menu.findItem(R.id.edit_feed_channel_menu);
            MenuItem refresh = menu.findItem(R.id.refresh_feed_channel_menu);
            MenuItem copy = menu.findItem(R.id.copy_feed_channel_url_menu);

            boolean show = selectedChannels.size() <= 1;
            edit.setVisible(show);
            refresh.setVisible(show);
            copy.setVisible(show);

            return true;
        }

        @Override
        public boolean onCreateActionMode(ActionMode mode, Menu menu)
        {
            inActionMode = true;
            mode.getMenuInflater().inflate(R.menu.feed_action_mode, menu);
            Utils.showActionModeStatusBar(activity, true);

            return true;
        }

        @Override
        public boolean onActionItemClicked(ActionMode mode, MenuItem item)
        {
            int i1 = item.getItemId();
            if (i1 == R.id.delete_feed_channel_menu) {
                mode.finish();
                if (getFragmentManager().findFragmentByTag(TAG_DELETE_CHANNELS_DIALOG) == null) {
                    BaseAlertDialog deleteTrackersDialog = BaseAlertDialog.newInstance(
                            getString(R.string.deleting),
                            (selectedChannels.size() > 1 ? getString(R.string.delete_selected_channels) :
                                    getString(R.string.delete_selected_channel)),
                            0,
                            getString(R.string.ok),
                            getString(R.string.cancel),
                            null,
                            FeedFragment.this);

                    deleteTrackersDialog.show(getFragmentManager(), TAG_DELETE_CHANNELS_DIALOG);
                }
            } else if (i1 == R.id.edit_feed_channel_menu) {
                mode.finish();
                if (getFragmentManager().findFragmentByTag(TAG_EDIT_CHANNEL_DIALOG) == null) {
                    AddRssChannelDialog addDialog = AddRssChannelDialog.newInstance(
                            activity.getApplicationContext(),
                            getString(R.string.edit_feed_channel),
                            getString(R.string.edit),
                            FeedFragment.this);
                    addDialog.show(getFragmentManager(), TAG_EDIT_CHANNEL_DIALOG);
                }
            } else if (i1 == R.id.copy_feed_channel_url_menu) {
                mode.finish();
                if (selectedChannels.size() != 0) {
                    ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Activity.CLIPBOARD_SERVICE);
                    ClipData clip;
                    clip = ClipData.newPlainText("URL", selectedChannels.get(0).getUrl());
                    clipboard.setPrimaryClip(clip);
                    Snackbar.make(coordinatorLayout,
                            R.string.link_copied_to_clipboard,
                            Snackbar.LENGTH_SHORT)
                            .show();
                    selectedChannels.clear();
                }
            } else if (i1 == R.id.select_all_channels_menu) {
                for (int i = 0; i < adapter.getItemCount(); i++) {
                    if (adapter.isSelected(i))
                        continue;
                    onItemSelected(adapter.getItem(i), i);
                }
            } else if (i1 == R.id.refresh_feed_channel_menu) {
                mode.finish();
                if (selectedChannels.size() != 0)
                    refreshChannels(selectedChannels);
                selectedChannels.clear();
            }

            return true;
        }

        @Override
        public void onDestroyActionMode(ActionMode mode)
        {
            adapter.clearSelection();
            actionMode = null;
            inActionMode = false;
            Utils.showActionModeStatusBar(activity, false);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent)
    {
        if (requestCode == EXPORT_FEEDS_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (intent.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                String path = intent.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);
                if (path != null) {
                    try {
                        String name = "Feeds-" + new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss")
                                .format(new Date(System.currentTimeMillis())) + "." + FeedStorage.SERIALIZE_FILE_FORMAT;
                        FileUtils.write(new File(path, name), FeedStorage.serializeChannels(channels),
                                Charset.forName("UTF-8"));

                        Snackbar.make(coordinatorLayout,
                                String.format(getString(R.string.export_feeds_successfully), name),
                                Snackbar.LENGTH_LONG)
                                .show();

                    } catch (IOException e) {
                        exportErrorDialog(e);
                    }
                }
            }

        } else if (requestCode == IMPORT_FEEDS_CHOOSE_REQUEST && resultCode == Activity.RESULT_OK) {
            if (intent.hasExtra(FileManagerDialog.TAG_RETURNED_PATH)) {
                String path = intent.getStringExtra(FileManagerDialog.TAG_RETURNED_PATH);
                if (path != null) {
                    try {
                        String data = FileUtils.readFileToString(new File(path), Charset.forName("UTF-8"));
                        ArrayList<FeedChannel> exported = FeedStorage.deserializeChannels(data);
                        channels.clear();
                        storage.addChannels(exported);
                        channels.addAll(storage.getAllChannels());
                        adapter.setItems(channels);
                        Snackbar.make(coordinatorLayout,
                                getString(R.string.import_feeds_successfully),
                                Snackbar.LENGTH_SHORT)
                                .show();

                        refreshChannels(channels);

                    } catch (Exception e) {
                        importErrorDialog(e);
                    }
                }
            }
        }
    }

    public void onBackPressed()
    {
        finish(new Intent(), FragmentCallback.ResultCode.OK);
    }

    private void finish(Intent intent, FragmentCallback.ResultCode code)
    {
        ((FragmentCallback)activity).fragmentFinished(intent, code);
    }
}
