/**
 * 
 */
package com.twitter.util;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.naming.InsufficientResourcesException;

import com.twitter.Constant;

import twitter4j.HashtagEntity;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.ResponseList;
import twitter4j.Status;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.User;
import twitter4j.auth.AccessToken;

/**
 * @author owaishussain@outlook.com
 */
public class TwitterUtil {

	private Twitter twitter;

	public TwitterUtil(Twitter twitter) {
		this.setTwitter(twitter);
	}

	public TwitterUtil(String consumerKey, String consumerSecret, String consumerToken, String consumerTokenSecret) {
		TwitterFactory factory = new TwitterFactory();
		setTwitter(factory.getInstance());
		getTwitter().setOAuthConsumer(consumerKey, consumerSecret);
		AccessToken accessToken = new AccessToken(consumerToken, consumerTokenSecret);
		getTwitter().setOAuthAccessToken(accessToken);
	}

	/**
	 * Get list of friends of given user
	 * 
	 * @param user
	 * @param delay
	 * @return
	 * @throws TwitterException
	 * @throws InterruptedException
	 */
	public List<User> getFriends(User user) throws TwitterException, InterruptedException {
		List<User> friendList = new ArrayList<User>();
		long cursor = -1;
		IDs friends = getTwitter().getFriendsIDs(user.getId(), cursor);
		do {
			for (long i : friends.getIDs()) {
				User friend = getTwitter().showUser(i);
				friendList.add(friend);
				Thread.sleep(Constant.DELAY);
			}
		} while (friends.hasNext());
		return friendList;
	}

	/**
	 * Get list of followers of given user
	 * 
	 * @param user
	 * @param delay
	 * @return
	 * @throws TwitterException
	 * @throws InterruptedException
	 */
	public List<User> getFollowers(User user) throws TwitterException, InterruptedException {
		ArrayList<User> followerList = new ArrayList<User>();
		long cursor = -1;
		IDs followers = getTwitter().getFollowersIDs(user.getId(), cursor);
		do {
			for (long i : followers.getIDs()) {
				User follower = getTwitter().showUser(i);
				followerList.add(follower);
				Thread.sleep(Constant.DELAY);
			}
		} while (followers.hasNext());
		return followerList;
	}

	/**
	 * Fetch timeline of given user
	 * 
	 * @param user
	 * @param pages
	 * @param attachHeader
	 * @return
	 * @throws TwitterException
	 */
	public List<String[]> getUserTimelineInRaw(User user, int pages, boolean attachHeader) throws TwitterException {
		ArrayList<String[]> content = new ArrayList<String[]>();
		if (attachHeader)
			content.add(Constant.TWEET_HEADER);
		for (int p = 1; p <= pages; p++) {
			Paging paging = new Paging(p);
			ResponseList<Status> timeline = twitter.getUserTimeline(user.getScreenName(), paging);
			for (Status status : timeline) {
				List<String> tweet = new ArrayList<String>();
				tweet.add(String.valueOf(status.getId()));
				tweet.add(String.valueOf(status.getUser().getId()));
				tweet.add(status.getUser().getScreenName());
				tweet.add(status.getText().replace("\n", " ").replace('"', '\''));
				// Get ; separated hash tags
				HashtagEntity[] hashtagEntities = status.getHashtagEntities();
				StringBuilder hashtags = new StringBuilder();
				for (HashtagEntity hashtag : hashtagEntities) {
					hashtags.append(hashtag.getText() + ";");
				}
				tweet.add(hashtags.toString());
				tweet.add(String.valueOf(status.isRetweet()));
				tweet.add(String.valueOf(status.isRetweeted()));
				tweet.add((status.getPlace() == null ? "" : status.getPlace().getCountryCode()));
				tweet.add(status.getInReplyToScreenName());
				tweet.add(status.getLang());
				tweet.add(String.valueOf(status.getCreatedAt().getTime()));
				tweet.add(String.valueOf(status.getFavoriteCount()));
				tweet.add(String.valueOf(status.getRetweetCount()));
				content.add(tweet.toArray(new String[] {}));
			}
		}
		return content;
	}
	
	public List<String[]> getFriendsInRaw(User user) throws TwitterException, InterruptedException {
		List<User> friends = getFriends(user);
		List<String[]> content = new ArrayList<String[]>();
		content.add(Constant.USER_HEADER);
		for (User friend : friends) {
			List<String> record = new ArrayList<String>();
			record.add(user.getScreenName());
			record.add(String.valueOf(friend.getId()));
			record.add(friend.getName());
			record.add(friend.getScreenName());
			record.add(String.valueOf(friend.getStatusesCount()));
			SimpleDateFormat sdf = new SimpleDateFormat(Constant.DATE_FORMAT);
			String dateCreated = sdf.format(friend.getCreatedAt());
			record.add(dateCreated);
			content.add(record.toArray(new String[] {}));
		}
		return content;
	}

	/**
	 * This method generates data set of users using Snowball sampling and returns a Map of users as
	 * keys and list of respective friends
	 * 
	 * @param starter user object from which to start sampling
	 * @param degree the degree of freedom to which sample is to be collected, e.g. friends of
	 *            friends of user is degree 2
	 * @param maxFriends maximum number of total friends to fetch per user. Pass -1 for no limit
	 * @throws TwitterException
	 * @throws InterruptedException
	 * @throws IOException
	 * @throws InsufficientResourcesException 
	 */
	public Map<User, List<User>> snowballSampling(User starter, int degree)
	        throws TwitterException, InterruptedException, IOException, InsufficientResourcesException {
		if (degree > 4) { // Will be too much
			throw new InsufficientResourcesException("Degree cannot be greater than 4.");
		}
		Map<User, List<User>> userMap = new HashMap<User, List<User>>();
		List<User> currentList = new ArrayList<User>();
		currentList.add(starter);
		int count = 0;
		while (count < degree) {
			User[] targetSet = currentList.toArray(new User[] {});
			currentList = new ArrayList<User>();
			for (User user : targetSet) {
				if (userMap.containsKey(user)) {
					continue;
				}
				List<User> friends = getFriends(user);
				userMap.put(user, friends);
				currentList.addAll(friends);
			}
			count++;
		}
		return userMap;
	}

	/**
	 * @return the twitter
	 */
	public Twitter getTwitter() {
		return twitter;
	}

	/**
	 * @param twitter the twitter to set
	 */
	public void setTwitter(Twitter twitter) {
		this.twitter = twitter;
	}
}
