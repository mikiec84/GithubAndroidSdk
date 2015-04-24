package com.alorma.github.sdk.services.notifications;

import android.content.Context;

import com.alorma.github.sdk.bean.dto.response.Notification;
import com.alorma.github.sdk.services.client.GithubClient;

import java.util.List;

import retrofit.RestAdapter;

/**
 * Created by Bernat on 18/02/2015.
 */
public class GetNotificationsClient extends GithubClient<List<Notification>> {

	private String since;

	public GetNotificationsClient(Context context) {
		super(context);
	}
	public GetNotificationsClient(Context context, String since) {
		super(context);
		this.since = since;
	}

	@Override
	protected void executeService(RestAdapter restAdapter) {
		if (since != null) {
			restAdapter.create(NotificationsService.class).getNotifications(since, this);
		} else {
			restAdapter.create(NotificationsService.class).getNotifications(this);
		}

	}

	@Override
	protected List<Notification> executeSync(RestAdapter restAdapter) {
		if (since != null) {
			return restAdapter.create(NotificationsService.class).getNotifications(since);
		} else {
			return restAdapter.create(NotificationsService.class).getNotifications();
		}
	}
}
