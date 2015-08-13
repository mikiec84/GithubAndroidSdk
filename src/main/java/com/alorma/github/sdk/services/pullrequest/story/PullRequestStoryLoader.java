package com.alorma.github.sdk.services.pullrequest.story;

import android.content.Context;
import android.util.Log;

import com.alorma.github.sdk.PullRequest;
import com.alorma.github.sdk.bean.dto.response.Commit;
import com.alorma.github.sdk.bean.dto.response.GithubComment;
import com.alorma.github.sdk.bean.dto.response.Label;
import com.alorma.github.sdk.bean.dto.response.ReviewComment;
import com.alorma.github.sdk.bean.info.IssueInfo;
import com.alorma.github.sdk.bean.issue.IssueEvent;
import com.alorma.github.sdk.bean.issue.IssueStoryComment;
import com.alorma.github.sdk.bean.issue.IssueStoryReviewComment;
import com.alorma.github.sdk.bean.issue.PullRequestStoryCommit;
import com.alorma.github.sdk.bean.issue.PullRequestStoryCommitsList;
import com.alorma.github.sdk.bean.issue.IssueStoryComparators;
import com.alorma.github.sdk.bean.issue.IssueStoryDetail;
import com.alorma.github.sdk.bean.issue.IssueStoryEvent;
import com.alorma.github.sdk.bean.issue.IssueStoryLabelList;
import com.alorma.github.sdk.bean.issue.IssueStoryUnlabelList;
import com.alorma.github.sdk.bean.issue.PullRequestStory;
import com.alorma.github.sdk.services.client.GithubClient;
import com.alorma.github.sdk.services.issues.story.IssueStoryService;
import com.alorma.github.sdk.services.pullrequest.PullRequestsService;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import retrofit.RestAdapter;

/**
 * Created by Bernat on 07/04/2015.
 */
public class PullRequestStoryLoader extends GithubClient<PullRequestStory> {

    private final IssueInfo issueInfo;
    private List<IssueStoryDetail> storyDetailMap;
    private PullRequestStory pullRequestStory;

    public PullRequestStoryLoader(Context context, IssueInfo info) {
        super(context);
        this.issueInfo = info;
    }

    @Override
    protected void executeService(RestAdapter adapter) {

        PullRequestStoryService pullRequestStoryService = adapter.create(PullRequestStoryService.class);
        IssueStoryService issueStoryService = adapter.create(IssueStoryService.class);
        PullRequestsService pullRequestsService = adapter.create(PullRequestsService.class);

        pullRequestStory = new PullRequestStory();
        storyDetailMap = new ArrayList<>();

        new PullRequestCallback(issueInfo, pullRequestStoryService, issueStoryService, pullRequestsService).execute();
    }

    @Override
    protected PullRequestStory executeServiceSync(RestAdapter restAdapter) {
        PullRequestStoryService pullRequestStoryService = restAdapter.create(PullRequestStoryService.class);
        IssueStoryService issueStoryService = restAdapter.create(IssueStoryService.class);
        PullRequestsService pullRequestsService = restAdapter.create(PullRequestsService.class);

        pullRequestStory = new PullRequestStory();
        storyDetailMap = new HashMap<>();
        int page = 1;
        boolean hasMore = true;

        pullRequestStory.pullRequest = pullRequestStoryService.detail(issueInfo.repoInfo.owner, issueInfo.repoInfo.name, issueInfo.num);

        while(hasMore){
            List<Label> issueLabels = issueStoryService.labels(issueInfo.repoInfo.owner, issueInfo.repoInfo.name, issueInfo.num, page);
            hasMore = pullRequestStory.pullRequest.labels.addAll(issueLabels);
            page++;
        }

        page = 1;

        while(true){
            List<GithubComment> issueComments = issueStoryService.comments(issueInfo.repoInfo.owner, issueInfo.repoInfo.name, issueInfo.num, page);
            hasMore = issueComments != null && !issueComments.isEmpty();
            if (!hasMore) break;
            for (GithubComment comment : issueComments) {
                long time = getMilisFromDate(comment.created_at);
                List<IssueStoryDetail> details = storyDetailMap.get(time);
                if (details == null) {
                    details = new ArrayList<>();
                    storyDetailMap.put(time, details);
                }
                details.add(new IssueStoryComment(comment));
            }
            page++;
        }

        page = 1;

        while(true){
            List<IssueEvent> issueEvents = issueStoryService.events(issueInfo.repoInfo.owner, issueInfo.repoInfo.name, issueInfo.num, page);
            hasMore = issueEvents != null && !issueEvents.isEmpty();
            if (!hasMore) break;
            for (IssueEvent event : issueEvents) {
                if (validEvent(event.event)) {
                    long time = getMilisFromDate(event.created_at);
                    List<IssueStoryDetail> details = storyDetailMap.get(time);
                    if (details == null) {
                        details = new ArrayList<>();
                        storyDetailMap.put(time, details);
                    }
                    details.add(new IssueStoryEvent(event));
                }
            }
            page++;
        }

        page = 1;

        while(true){
            List<Commit> commits = pullRequestsService.commits(issueInfo.repoInfo.owner, issueInfo.repoInfo.name, issueInfo.num, page);
            hasMore = commits != null && !commits.isEmpty();
            if (!hasMore) break;
            for (Commit commit : commits) {
                if (commit.committer != null) {
                    String date = commit.commit.committer.date;
                    if (date != null) {
                        long time = getMilisFromDate(date);
                        List<IssueStoryDetail> commitsDetails = storyDetailMap.get(time);
                        if (commitsDetails == null) {
                            commitsDetails = new ArrayList<>();
                            storyDetailMap.put(time, commitsDetails);
                        }
                        commitsDetails.add(new IssueStoryCommit(commit));
                    }
                }
            }
            page++;
        }


        return parseIssueStoryDetails();
    }

    private void parseIssueStoryDetails() {
        Collections.sort(storyDetailMap, IssueStoryComparators.ISSUE_STORY_DETAIL_COMPARATOR);
        pullRequestStory.details = storyDetailMap;

        if (getOnResultCallback() != null) {
            getOnResultCallback().onResponseOk(pullRequestStory, null);
        }
    }

    private class PullRequestCallback extends BaseInfiniteCallback<PullRequest> {

        private final IssueInfo info;
        private final PullRequestStoryService pullRequestStoryService;
        private final IssueStoryService issueStoryService;
        private final PullRequestsService pullRequestsService;

        public PullRequestCallback(IssueInfo info, PullRequestStoryService pullRequestStoryService, IssueStoryService issueStoryService, PullRequestsService pullRequestsService) {
            this.info = info;
            this.pullRequestStoryService = pullRequestStoryService;
            this.issueStoryService = issueStoryService;
            this.pullRequestsService = pullRequestsService;
        }

        @Override
        public void execute() {
            pullRequestStoryService.detail(info.repoInfo.owner, info.repoInfo.name, info.num, this);
        }

        @Override
        protected void executePaginated(int nextPage) {

        }

        @Override
        protected void executeNext() {
            new LabelsCallback(issueInfo, issueStoryService, pullRequestsService).execute();
        }

        @Override
        protected void response(PullRequest pullRequest) {
            pullRequestStory.pullRequest = pullRequest;
            pullRequestStory.pullRequest.labels = new ArrayList<>();
        }

    }

    private class LabelsCallback extends BaseInfiniteCallback<List<Label>> {

        private final IssueInfo info;
        private final IssueStoryService issueStoryService;
        private final PullRequestsService pullRequestsService;

        public LabelsCallback(IssueInfo info, IssueStoryService issueStoryService, PullRequestsService pullRequestsService) {
            this.info = info;
            this.issueStoryService = issueStoryService;
            this.pullRequestsService = pullRequestsService;
        }


        @Override
        public void execute() {
            issueStoryService.labels(info.repoInfo.owner, info.repoInfo.name, info.num, this);
        }

        @Override
        protected void executePaginated(int nextPage) {
            issueStoryService.labels(info.repoInfo.owner, info.repoInfo.name, info.num, nextPage, this);
        }

        @Override
        protected void executeNext() {
            new IssueCommentsCallback(issueInfo, issueStoryService, pullRequestsService).execute();
        }

        @Override
        protected void response(List<Label> issueLabels) {
            pullRequestStory.pullRequest.labels.addAll(issueLabels);
        }
    }

    private class IssueCommentsCallback extends BaseInfiniteCallback<List<GithubComment>> {

        private final IssueInfo info;
        private final IssueStoryService issueStoryService;
        private final PullRequestsService pullRequestsService;

        public IssueCommentsCallback(IssueInfo info, IssueStoryService issueStoryService, PullRequestsService pullRequestsService) {
            this.info = info;
            this.issueStoryService = issueStoryService;
            this.pullRequestsService = pullRequestsService;
        }

        @Override
        public void execute() {
            issueStoryService.comments(info.repoInfo.owner, info.repoInfo.name, info.num, this);
        }

        @Override
        protected void executePaginated(int nextPage) {
            issueStoryService.comments(info.repoInfo.owner, info.repoInfo.name, info.num, nextPage, this);
        }

        @Override
        protected void executeNext() {
            new IssueEventsCallbacks(info, issueStoryService, pullRequestsService).execute();
        }

        @Override
        protected void response(List<GithubComment> issueComments) {
            for (GithubComment comment : issueComments) {
                long time = getMilisFromDateClearDay(comment.created_at);
                IssueStoryComment detail = new IssueStoryComment(comment);
                detail.created_at = time;
                storyDetailMap.add(detail);
            }
        }
    }

    private class IssueEventsCallbacks extends BaseInfiniteCallback<List<IssueEvent>> {

        private IssueInfo info;
        private IssueStoryService issueStoryService;
        private PullRequestsService pullRequestsService;

        public IssueEventsCallbacks(IssueInfo info, IssueStoryService issueStoryService, PullRequestsService pullRequestsService) {
            this.info = info;
            this.issueStoryService = issueStoryService;
            this.pullRequestsService = pullRequestsService;
        }

        @Override
        public void execute() {
            issueStoryService.events(info.repoInfo.owner, info.repoInfo.name, info.num, this);
        }

        @Override
        protected void executePaginated(int nextPage) {
            issueStoryService.events(info.repoInfo.owner, info.repoInfo.name, info.num, nextPage, this);
        }

        @Override
        protected void executeNext() {
           parseIssueStoryDetails();
        }

        @Override
        protected void response(List<IssueEvent> issueEvents) {
            List<IssueStoryDetail> details = new ArrayList<>();

            Map<Long, IssueStoryLabelList> labeledEvents = new HashMap<>();
            Map<Long, IssueStoryUnlabelList> unlabeledEvents = new HashMap<>();
            for (IssueEvent event : issueEvents) {
                String createdAt = event.created_at;
                long time = getMilisFromDateClearDay(createdAt);
                if (event.event.equals("labeled")) {
                    if (labeledEvents.get(time) == null) {
                        labeledEvents.put(time, new IssueStoryLabelList());
                        labeledEvents.get(time).created_at = time;
                        labeledEvents.get(time).user = event.actor;
                    }
                    labeledEvents.get(time).add(event.label);
                } else if (event.event.equals("unlabeled")) {
                    if (unlabeledEvents.get(time) == null) {
                        unlabeledEvents.put(time, new IssueStoryUnlabelList());
                        unlabeledEvents.get(time).created_at = time;
                        unlabeledEvents.get(time).user = event.actor;
                    }
                    unlabeledEvents.get(time).add(event.label);
                } else {
                    IssueStoryEvent issueStoryEvent = new IssueStoryEvent(event);
                    if (validEvent(issueStoryEvent.getType())) {
                        issueStoryEvent.created_at = time;
                        details.add(issueStoryEvent);
                    }
                }
            }

            for (Long aLong : labeledEvents.keySet()) {
                IssueStoryLabelList issueLabels = labeledEvents.get(aLong);
                issueLabels.created_at = aLong;
                details.add(issueLabels);
            }

            for (Long aLong : unlabeledEvents.keySet()) {
                IssueStoryUnlabelList issueLabels = unlabeledEvents.get(aLong);
                issueLabels.created_at = aLong;
                details.add(issueLabels);
            }

            storyDetailMap.addAll(details);
        }
    }

    private long getMilisFromDateClearDay(String createdAt) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

        DateTime dt = formatter.parseDateTime(createdAt);

        return dt.minuteOfDay().roundFloorCopy().getMillis();
    }

    private long getMilisFromDateClearHour(String createdAt) {
        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

        DateTime dt = formatter.parseDateTime(createdAt);

        return dt.hourOfDay().roundFloorCopy().getMillis();
    }

    @Override
    public void log(String message) {
        Log.i("IssueStoryLoader", message);
    }

    @Override
    public String getAcceptHeader() {
        return "application/vnd.github.v3.full+json";
    }
}
