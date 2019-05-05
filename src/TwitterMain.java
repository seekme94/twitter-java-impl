
/**
 * 
 */
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.twitter.util.ClassLoaderUtil;
import com.twitter.util.FileUtil;

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
 * @author owais.hussain@irdresearch.org
 */
public class TwitterMain {

	private static final String DATE_FORMAT = "yyyy-MM-dd";

	private static final String[] USER_HEADER = { "user", "connection_id", "connection_name", "screen_name", "status_count",
	        "date_created" };

	private static final String[] TWEET_HEADER = { "tweet_id", "user_id", "screen_name", "text", "hashtags", "retweet",
	        "retweeted", "location", "reply_to_screen_name", "lang", "timestamp", "fav_count", "rt_count" };

	private static int delay = 2000; // delay between calls

	private static String consumerKey = "my_consumer_key";

	private static String consumerSecret = "my_consumer_secret";

	private static String consumerToken = "my_consumer_token";

	private static String consumerTokenSecret = "my_consumer_token_secret";

	private static Twitter twitter;

	private static String saveFilePath = "savefile.csv";

	private static String screenNamesFilePath = "res/screennamesfile.txt"; // 1 line per name

	private static boolean appendFile = true;

	private static boolean fetchTweets = true;

	private static boolean fetchFriends = false;

	private static FileUtil fileUtil = new FileUtil();

	public static void main(String[] args) throws InterruptedException, IOException {
		// Read application properties
		readProperties("twitter.properties");
		// Check arguments
		if (args != null) {
			readArguments(args);
		}
		TwitterFactory factory = new TwitterFactory();
		twitter = factory.getInstance();
		twitter.setOAuthConsumer(consumerKey, consumerSecret);
		AccessToken accessToken = new AccessToken(consumerToken, consumerTokenSecret);
		twitter.setOAuthAccessToken(accessToken);
		TwitterMain twitterMain = new TwitterMain();
		List<String> screenNames = fileUtil.readLines(screenNamesFilePath);

		if (fetchFriends) {
			for (String name : screenNames) {
				try {
					User user = twitter.showUser(name);
					System.out.println("Fetching friends of: " + user.getScreenName());
					List<User> friends = twitterMain.getFriends(user);
					twitterMain.writeConnectionsToFile(user, friends, appendFile);
				}
				catch (Exception e) {
					e.printStackTrace();
					Thread.sleep(delay);
				}
			}
		}
		if (fetchTweets) {
			for (String name : screenNames) {
				try {
					for (int p = 1; p <= 20; p++) {
						Paging paging = new Paging(p);
						ResponseList<Status> timeline = twitter.getUserTimeline(name, paging);
						List<String[]> content = convertRawTweets(timeline, true);
						write(saveFilePath, content, true);
						Thread.sleep(delay);
					}
				}
				catch (IllegalArgumentException e) {}
				catch (TwitterException e) {
					// In case of rate limit exception, delay for the time said
					if (e.getStatusCode() == 401) {
						int timeInSeconds = e.getRateLimitStatus().getSecondsUntilReset();
						Thread.sleep((timeInSeconds + delay));
					}
				}
				catch (Exception e) {
					System.out.println(name + " caused Exception: " + e.getCause());
				}
			}
		}
	}

	/**
	 * @param fileName
	 * @throws IOException
	 */
	private static void readProperties(String fileName) throws IOException {
		URL file = ClassLoaderUtil.getResource(fileName, TwitterMain.class);
		Properties prop = new Properties();
		prop.load(file.openStream());
		consumerKey = prop.getProperty("consumer_key");
		consumerSecret = prop.getProperty("consumer_secret");
		consumerToken = prop.getProperty("consumer_token");
		consumerTokenSecret = prop.getProperty("consumer_token_secret");
	}

	/**
	 * @param args
	 */
	private static void readArguments(String[] args) {
		for (int i = 0; i < args.length; i++) {
			if (args[i].equals("-o")) {
				saveFilePath = args[++i];
			} else if (args[i].equals("-d")) {
				delay = Integer.parseInt(args[++i]);
			} else if (args[i].equals("-l")) {
				screenNamesFilePath = args[++i];
			} else if (args[i].equals("-c")) {
				appendFile = false;
			} else if (args[i].equals("-t")) {
				fetchTweets = true;
			} else if (args[i].equals("-f")) {
				fetchFriends = true;
			}
		}
	}

	private static List<String[]> convertRawTweets(ResponseList<Status> timeline, boolean attachHeader) {
		ArrayList<String[]> content = new ArrayList<String[]>();
		if (attachHeader)
			content.add(TWEET_HEADER);
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
			System.out.println(Arrays.toString(tweet.toArray(new String[] {})));
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
	 */
	public Map<User, List<User>> snowballSampling(User starter, int degree, boolean writeToFile)
	        throws TwitterException, InterruptedException, IOException {
		if (degree > 4) { // Will be too much
			System.out.println("Degree cannot be greater than 4.");
			return null;
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
				if (writeToFile) {
					writeConnectionsToFile(user, friends, true);
				}
			}
			count++;
		}
		return userMap;
	}

	public void writeConnectionsToFile(User from, List<User> connections, boolean append) throws IOException {
		List<String[]> content = new ArrayList<String[]>();
		content.add(USER_HEADER);
		for (User u : connections) {
			List<String> record = new ArrayList<String>();
			record.add(from.getScreenName());
			record.add(String.valueOf(u.getId()));
			record.add(u.getName());
			record.add(u.getScreenName());
			record.add(String.valueOf(u.getStatusesCount()));
			SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT);
			String dateCreated = sdf.format(u.getCreatedAt());
			record.add(dateCreated);
			content.add(record.toArray(new String[] {}));
		}
		write(saveFilePath, content, append);
	}

	public List<User> getFriends(User user) throws TwitterException, InterruptedException {
		List<User> friendList = new ArrayList<User>();
		long cursor = -1;
		IDs friends = twitter.getFriendsIDs(user.getId(), cursor);
		do {
			for (long i : friends.getIDs()) {
				User friend = twitter.showUser(i);
				friendList.add(friend);
				Thread.sleep(delay);
			}
		} while (friends.hasNext());
		return friendList;
	}

	/**
	 * Get list of followers of given user
	 * 
	 * @param user
	 * @return
	 * @throws TwitterException
	 */
	public List<User> getFollowers(User user) throws TwitterException {
		ArrayList<User> followerList = new ArrayList<User>();
		long cursor = -1;
		IDs followers = twitter.getFollowersIDs(user.getId(), cursor);
		do {
			for (long i : followers.getIDs()) {
				User follower = twitter.showUser(i);
				followerList.add(follower);
			}
		} while (followers.hasNext());
		return followerList;
	}

	/**
	 * Write the contents to given file
	 * 
	 * @param filePath
	 * @param content
	 * @param append
	 * @throws IOException
	 */
	public static void write(String filePath, List<String[]> content, boolean append) throws IOException {
		fileUtil.writeCsv(filePath, content, ',', true, append);
	}
}
