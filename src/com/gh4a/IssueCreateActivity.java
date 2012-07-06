/*
 * Copyright 2011 Azwan Adli Abdullah
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gh4a;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.Label;
import org.eclipse.egit.github.core.Milestone;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.client.GitHubClient;
import org.eclipse.egit.github.core.service.IssueService;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.LoaderManager;
import android.support.v4.content.Loader;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.gh4a.loader.CollaboratorListLoader;
import com.gh4a.loader.LabelListLoader;
import com.gh4a.loader.MilestoneListLoader;
import com.gh4a.utils.StringUtils;

public class IssueCreateActivity extends BaseSherlockFragmentActivity 
    implements OnClickListener, LoaderManager.LoaderCallbacks {

    private String mRepoOwner;
    private String mRepoName;
    private ActionBar mActionBar;
    private LoadingDialog mLoadingDialog;
    private List<Label> mSelectedLabels;
    private Milestone mSelectedMilestone;
    private User mSelectedAssignee;
    private EditText mEtTitle;
    private EditText mEtDesc;
    private Button mBtnMilestone;
    private Button mBtnAssignee;
    private TextView mTvSelectedMilestone;
    private TextView mTvSelectedAssignee;
    private List<Label> mAllLabel;
    private List<Milestone> mAllMilestone;
    private List<User> mAllAssignee;
    private LinearLayout mLinearLayoutLabels;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        if (!isAuthorized()) {
            Intent intent = new Intent().setClass(this, Github4AndroidActivity.class);
            startActivity(intent);
            finish();
        }
        setContentView(R.layout.issue_create);

        mSelectedLabels = new ArrayList<Label>();
        Bundle data = getIntent().getExtras();
        
        mRepoOwner = data.getString(Constants.Repository.REPO_OWNER);
        mRepoName = data.getString(Constants.Repository.REPO_NAME);

        mActionBar = getSupportActionBar();
        mActionBar.setTitle(R.string.issue_create);
        mActionBar.setSubtitle(mRepoOwner + "/" + mRepoName);
        mActionBar.setDisplayShowTitleEnabled(true);
        mActionBar.setHomeButtonEnabled(true);
        
        mEtTitle = (EditText) findViewById(R.id.et_title);
        mEtDesc = (EditText) findViewById(R.id.et_desc);
        
        mBtnMilestone = (Button) findViewById(R.id.btn_milestone);
        mBtnMilestone.setOnClickListener(this);
        
        mBtnAssignee = (Button) findViewById(R.id.btn_assignee);
        mBtnAssignee.setOnClickListener(this);
        
        mLinearLayoutLabels = (LinearLayout) findViewById(R.id.ll_labels);
        mTvSelectedMilestone = (TextView) findViewById(R.id.tv_selected_milestone);
        mTvSelectedMilestone.setTypeface(getApplicationContext().boldCondensed);
        
        mTvSelectedAssignee = (TextView) findViewById(R.id.tv_selected_assignee);
        mTvSelectedAssignee.setTypeface(getApplicationContext().boldCondensed);
        
        TextView tvIssueLabelAdd = (TextView) findViewById(R.id.tv_issue_label_add);
        tvIssueLabelAdd.setTypeface(getApplicationContext().boldCondensed);
        tvIssueLabelAdd.setTextColor(Color.parseColor("#0099cc"));
        
        getSupportLoaderManager().initLoader(0, null, this);
        getSupportLoaderManager().getLoader(0).forceLoad();
        
        getSupportLoaderManager().initLoader(1, null, this);
        getSupportLoaderManager().initLoader(2, null, this);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.accept_cancel, menu);
        return super.onCreateOptionsMenu(menu);
    }
    
    @Override
    public boolean setMenuOptionItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.accept:
            if (mEtTitle.getText() == null || StringUtils.isBlank(mEtTitle.getText().toString())) {
                showMessage(getResources().getString(R.string.issue_error_title), false);
            }
            else {
                new CreateIssueTask(this, false).execute();
            }
            return true;

        case R.id.cancel:
            getApplicationContext().openIssueListActivity(this, mRepoOwner, mRepoName, Constants.Issue.ISSUE_STATE_OPEN);
            return true;
        
        default:
            return true;
        }
    }
    
    @Override
    public void onClick(View view) {
        switch (view.getId()) {

        case R.id.btn_milestone:
            if (mAllMilestone == null) {
                getSupportLoaderManager().getLoader(1).forceLoad();
            }
            else {
                showMilestonesDialog();
            }
            break;
            
        case R.id.btn_assignee:
            if (mAllAssignee == null) {
                getSupportLoaderManager().getLoader(2).forceLoad();
            }
            else {
                showAssigneesDialog();
            }
            break;

        default:
            break;
        }
    }

    private static class CreateIssueTask extends AsyncTask<String, Void, Boolean> {

        private WeakReference<IssueCreateActivity> mTarget;
        private boolean mException;
        private boolean mHideMainView;

        public CreateIssueTask(IssueCreateActivity activity, boolean hideMainView) {
            mTarget = new WeakReference<IssueCreateActivity>(activity);
            mHideMainView = hideMainView;
        }

        @Override
        protected Boolean doInBackground(String... params) {
            if (mTarget.get() != null) {
                try {
                    IssueCreateActivity activity = mTarget.get();
                    GitHubClient client = new GitHubClient();
                    client.setOAuth2Token(mTarget.get().getAuthToken());
                    IssueService issueService = new IssueService(client);
                    
                    Issue issue = new Issue();
                    issue.setTitle(activity.mEtTitle.getText().toString());
                    issue.setBody(activity.mEtDesc.getText().toString());
                    
                    if (activity.mSelectedMilestone != null) {
                        issue.setMilestone(activity.mSelectedMilestone);
                    }
                
                    if (activity.mSelectedLabels != null && !activity.mSelectedLabels.isEmpty()) {
                        issue.setLabels(activity.mSelectedLabels);
                    }
                    
                    if (activity.mSelectedAssignee != null) {
                        issue.setAssignee(activity.mSelectedAssignee);
                    }
                    
                    issueService.createIssue(activity.mRepoOwner, 
                            activity.mRepoName, issue);
                    
                    return true;
                }
                catch (IOException e) {
                    Log.e(Constants.LOG_TAG, e.getMessage(), e);
                    mException = true;
                    return null;
                }
            }
            else {
                return false;
            }
        }

        @Override
        protected void onPreExecute() {
            if (mTarget.get() != null) {
                mTarget.get().mLoadingDialog = LoadingDialog.show(mTarget.get(), true, true, mHideMainView);
            }
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (mTarget.get() != null) {
                IssueCreateActivity activity = mTarget.get();
                activity.mLoadingDialog.dismiss();
    
                if (mException) {
                    activity.showError(false);
                }
                else {
                    activity.showMessage(activity.getResources().getString(R.string.issue_success_create),
                            false);
                    activity.getApplicationContext().openIssueListActivity(activity, 
                            activity.mRepoOwner, 
                            activity.mRepoName, 
                            Constants.Issue.ISSUE_STATE_OPEN);
                }
            }
        }
    }
    
    private void showMilestonesDialog() {
        final String[] milestones = new String[mAllMilestone.size() + 1];
        
        milestones[0] = getResources().getString(R.string.issue_clear_milestone);
        
        int checkedItem = 0;
        if (mSelectedMilestone != null) {
            checkedItem = mSelectedMilestone.getNumber();
        }
        
        for (int i = 1; i <= mAllMilestone.size(); i++) {
            Milestone m = mAllMilestone.get(i - 1);
            milestones[i] = m.getTitle();
            if (m.getNumber() == checkedItem) {
                checkedItem = i;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme));
        builder.setCancelable(true);
        builder.setTitle(R.string.issue_milestone);
        builder.setSingleChoiceItems(milestones, checkedItem, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    mSelectedMilestone = null;
                }
                else {
                    mSelectedMilestone = mAllMilestone.get(which - 1);
                }
            }
        });
        
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mSelectedMilestone != null) {
                    mTvSelectedMilestone.setText(mSelectedMilestone.getTitle());
                }
                else {
                    mTvSelectedAssignee.setText(null);
                }
                dialog.dismiss();
            }
        })
        .setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        })
       .create();
        
        builder.show();
    }
    
    private void showAssigneesDialog() {
        final String[] assignees = new String[mAllAssignee.size() + 1];
        assignees[0] = getResources().getString(R.string.issue_clear_assignee);
        
        int checkedItem = 0;
        
        for (int i = 1; i <= mAllAssignee.size(); i++) {
            User u = mAllAssignee.get(i - 1);
            assignees[i] = u.getLogin();
            if (mSelectedAssignee != null
                    && u.getLogin().equalsIgnoreCase(mSelectedAssignee.getLogin())) {
                checkedItem = i;
            }
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(new ContextThemeWrapper(this, android.R.style.Theme));
        builder.setCancelable(true);
        builder.setTitle(R.string.issue_assignee);
        builder.setSingleChoiceItems(assignees, checkedItem, new DialogInterface.OnClickListener() {
            
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    mSelectedAssignee = null;
                }
                else {
                    mSelectedAssignee = mAllAssignee.get(which - 1);
                }
            }
        });
        
        builder.setPositiveButton(R.string.ok,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                if (mSelectedAssignee != null) {
                    mTvSelectedAssignee.setText(mSelectedAssignee.getLogin());
                }
                else {
                    mTvSelectedAssignee.setText(null);
                }
                dialog.dismiss();
            }
        })
        .setNegativeButton(R.string.cancel,
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        })
       .create();
        
        builder.show();
    }
    
    public void fillLabels() {
        final Typeface boldCondensed = getApplicationContext().boldCondensed;
        final Typeface condensed = getApplicationContext().condensed;
        
        for (final Label label : mAllLabel) {
            final View rowView = getLayoutInflater().inflate(R.layout.row_issue_create_label, null);
            View viewColor = (View) rowView.findViewById(R.id.view_color);
            viewColor.setBackgroundColor(Color.parseColor("#" + label.getColor()));
            //viewColor.setPadding(10, 10, 10, 10);
            
            final TextView tvLabel = (TextView) rowView.findViewById(R.id.tv_title);
            tvLabel.setTypeface(condensed);
            tvLabel.setText(label.getName());
            tvLabel.setOnClickListener(new OnClickListener() {
                
                @Override
                public void onClick(View v) {
                    if (mSelectedLabels.contains(label)) {
                        mSelectedLabels.remove(label);
                        
                        tvLabel.setTypeface(condensed);
                        tvLabel.setBackgroundColor(Color.WHITE);
                        tvLabel.setTextColor(getResources().getColor(android.R.color.primary_text_light));
                    }
                    else {
                        mSelectedLabels.add(label);
                        
                        tvLabel.setTypeface(boldCondensed);
                        tvLabel.setBackgroundColor(Color.parseColor("#" + label.getColor()));
                        int r = Color.red(Color.parseColor("#" + label.getColor()));
                        int g = Color.green(Color.parseColor("#" + label.getColor()));
                        int b = Color.blue(Color.parseColor("#" + label.getColor()));
                        if (r + g + b < 383) {
                            tvLabel.setTextColor(getResources().getColor(android.R.color.primary_text_dark));
                        }
                        else {
                            tvLabel.setTextColor(getResources().getColor(android.R.color.primary_text_light));
                        }                    
                    }
                }
            });
            
            mLinearLayoutLabels.addView(rowView);
        }
    }
    
    @Override
    public Loader onCreateLoader(int id, Bundle args) {
        if (id == 0) {
            return new LabelListLoader(this, mRepoOwner, mRepoName);
        }
        else if (id == 1) {
            return new MilestoneListLoader(this, mRepoOwner, mRepoName, "open");
        }
        else {
            return new CollaboratorListLoader(this, mRepoOwner, mRepoName);
        }
    }

    @Override
    public void onLoadFinished(Loader loader, Object object) {
        if (loader.getId() == 0) {
            mAllLabel = (List<Label>) object;
            fillLabels();
        }
        else if (loader.getId() == 1) {
            mAllMilestone = (List<Milestone>) object;
            showMilestonesDialog();
        }
        else {
            mAllAssignee = (List<User>) object;
            showAssigneesDialog();
        }
    }

    @Override
    public void onLoaderReset(Loader arg0) {
        // TODO Auto-generated method stub
        
    }

}
