
/**
 * 
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import twitter4j.HashtagEntity;
import twitter4j.IDs;
import twitter4j.Paging;
import twitter4j.Query;
import twitter4j.QueryResult;
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
	private static String CONSUMER_KEY = "my_consumer_key";
	private static String CONSUMER_SECRET = "my_consumer_secret";
	private static String CONSUMER_TOKEN = "my_consumer_token";
	private static String CONSUMER_TOKEN_SECRET = "my_consumer_token_secret";

	private static Twitter twitter;

	private static String saveFilePath = "savefile.csv";
	private static String screenNamesFilePath = "screennamesfile.txt"; // 1 line per name
	private static boolean appendFile = true;
	private static int delay = 2000; // delay between calls
	private static boolean fetchTweets = false;
	private static boolean fetchFriends = false;

	public static void main(String[] args) throws InterruptedException, IOException {
		// Read application properties
		readProperties("twitter.properties");
		// Check arguments
		if (args != null) {
			readArguments(args);
		}
		
		TwitterFactory factory = new TwitterFactory();
		twitter = factory.getInstance();
		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		AccessToken accessToken = new AccessToken(CONSUMER_TOKEN, CONSUMER_TOKEN_SECRET);
		twitter.setOAuthAccessToken(accessToken);
		TwitterMain twitterMain = new TwitterMain();
		
		// Read screen names from file
		List<String> screenNames = twitterMain.readLines(screenNamesFilePath);

		if (fetchFriends) {
			for (String name : screenNames) {
				try {
					User user = twitter.showUser(name);
					ArrayList<User> friends = twitterMain.getFriends(user);
					twitterMain.writeConnections(user, friends, appendFile);
				} catch (Exception e) {
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
						String[] content = convertRawTweets(timeline, true);
						twitterMain.writeCsv(saveFilePath, content, true, true);
						Thread.sleep(delay);
					}
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (TwitterException e) {
					e.printStackTrace();
					// In case of rate limit exception, delay for the time said
					if (e.getStatusCode() == 401) {
						int timeInSeconds = e.getRateLimitStatus().getSecondsUntilReset();
						Thread.sleep((timeInSeconds + delay) * 1000);
					}
				} catch (Exception e) {
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
		file.getFile();
		Properties prop = new Properties();
		prop.load(file.openStream());
		CONSUMER_KEY = prop.getProperty("consumer_key");
		CONSUMER_SECRET = prop.getProperty("consumer_secret");
		CONSUMER_TOKEN = prop.getProperty("consumer_token");
		CONSUMER_TOKEN_SECRET = prop.getProperty("consumer_token_secret");
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

	private static String[] convertRawTweets(ResponseList<Status> timeline, boolean attachHeader) {
		ArrayList<String> content = new ArrayList<String>();
		if (attachHeader)
			content.add(
					"tweet_id,user_id,screen_name,text,hashtags,retweet,retweeted,location,reply_to_screen_name,lang,timestamp,fav_count,rt_count");
		for (Status status : timeline) {
			try {
				StringBuilder sb = new StringBuilder();
				sb.append("\"" + status.getId() + "\",");
				sb.append("\"" + status.getUser().getId() + "\",");
				sb.append("\"" + status.getUser().getScreenName() + "\",");
				sb.append("\"" + status.getText().replace("\n", " ").replace('"', '\'') + "\",");
				// Get ; separated hash tags
				HashtagEntity[] hashtagEntities = status.getHashtagEntities();
				String hashtags = "";
				for (HashtagEntity hashtag : hashtagEntities) {
					hashtags += hashtag.getText() + ";";
				}
				sb.append("\"" + hashtags + "\",");
				sb.append("\"" + status.isRetweet() + "\",");
				sb.append("\"" + status.isRetweeted() + "\",");
				sb.append("\"" + (status.getPlace() == null ? "" : status.getPlace().getCountryCode()) + "\",");
				sb.append("\"" + status.getInReplyToScreenName() + "\",");
				sb.append("\"" + status.getLang() + "\",");
				sb.append("\"" + status.getCreatedAt().getTime() + "\",");
				sb.append("\"" + status.getFavoriteCount() + "\",");
				sb.append("\"" + status.getRetweetCount() + "\"");
				content.add(sb.toString());
				System.out.println(sb.toString());
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		return content.toArray(new String[] {});
	}

	/**
	 * This method generates data set of users using Snowball sampling and
	 * returns a Map of users as keys and list of respective friends
	 * 
	 * @param starter
	 *            user object from which to start sampling
	 * @param degree
	 *            the degree of freedom to which sample is to be collected, e.g.
	 *            friends of friends of user is degree 2
	 * @param maxFriends
	 *            maximum number of total friends to fetch per user. Pass -1 for
	 *            no limit
	 * @throws TwitterException
	 * @throws InterruptedException
	 */
	public Map<User, ArrayList<User>> snowballSampling(User starter, int degree, boolean writeToFile)
			throws TwitterException, InterruptedException {
		if (degree > 4) { // Will be too much
			System.out.println("Degree cannot be greater than 4.");
			return null;
		}
		Map<User, ArrayList<User>> userMap = new HashMap<User, ArrayList<User>>();
		ArrayList<User> currentList = new ArrayList<User>();
		currentList.add(starter);
		int count = 0;
		while (count < degree) {
			User[] targetSet = currentList.toArray(new User[] {});
			currentList = new ArrayList<User>();
			for (User u : targetSet) {
				if (userMap.containsKey(u)) {
					continue;
				}
				ArrayList<User> friends = getFriends(u);
				userMap.put(u, friends);
				currentList.addAll(friends);
				if (writeToFile) {
					writeConnections(u, friends, true);
				}
			}
			count++;
		}
		return userMap;
	}

	public void writeConnections(User from, ArrayList<User> connections, boolean append) {
		ArrayList<String> content = new ArrayList<String>();
		content.add("user,connection_id,connection_name,screen_name,status_count,date_created");
		for (User u : connections) {
			StringBuilder record = new StringBuilder();
			record.append(from.getScreenName() + ",");
			record.append(u.getId() + ",");
			record.append(u.getName() + ",");
			record.append(u.getScreenName() + ",");
			record.append(u.getStatusesCount() + ",");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			String dateCreated = sdf.format(u.getCreatedAt());
			record.append(dateCreated);
			content.add(record.toString());
		}
		writeCsv(saveFilePath, content.toArray(new String[] {}), append);
	}

	public ArrayList<User> getFriends(User user) throws TwitterException, InterruptedException {
		ArrayList<User> friendList = new ArrayList<User>();
		long cursor = -1;
		System.out.println("Fetching friends of: " + user.getScreenName());
		IDs friends = twitter.getFriendsIDs(user.getId(), cursor);
		do {
			for (long i : friends.getIDs()) {
				User friend = twitter.showUser(i);
				friendList.add(friend);
				StringBuilder record = new StringBuilder();
				record.append(user.getScreenName() + ",");
				record.append(friend.getId() + ",");
				record.append(friend.getName() + ",");
				record.append(friend.getScreenName() + ",");
				record.append(friend.getStatusesCount() + ",");
				SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
				String dateCreated = sdf.format(friend.getCreatedAt());
				record.append(dateCreated);
				System.out.println(record.toString());
				Thread.sleep(1000);
			}
		} while (friends.hasNext());
		return friendList;
	}

	public ArrayList<User> getFollowers(User user) throws TwitterException {
		ArrayList<User> followerList = new ArrayList<User>();
		long cursor = -1;
		IDs followers = twitter.getFollowersIDs(user.getId(), cursor);
		do {
			for (long i : followers.getIDs()) {
				User follower = twitter.showUser(i);
				followerList.add(follower);
				System.out.println(follower.getScreenName());
			}
		} while (followers.hasNext());
		return followerList;
	}

	public void writeTweets(String queryString) throws TwitterException, InterruptedException {
		twitter.setOAuthConsumer(CONSUMER_KEY, CONSUMER_SECRET);
		AccessToken accessToken = new AccessToken(CONSUMER_TOKEN, CONSUMER_TOKEN_SECRET);
		twitter.setOAuthAccessToken(accessToken);

		ArrayList<String> tweets = new ArrayList<String>();
		File f = new File(saveFilePath);
		// Write header
		if (!f.exists())
			tweets.add(
					"tweet_id,user_id,screen_name,text,hashtags,retweet,retweeted,location,reply_to_screen_name,lang,timestamp,fav_count,rt_count");
		while (true) {
			Query query = new Query(queryString);
			QueryResult result = twitter.search(query);
			for (Status status : result.getTweets()) {
				StringBuilder sb = new StringBuilder();
				sb.append("\"" + status.getId() + "\",");
				sb.append("\"" + status.getUser().getId() + "\",");
				sb.append("\"" + status.getUser().getScreenName() + "\",");
				sb.append("\"" + status.getText().replace("\n", " ").replace('"', '\'') + "\",");
				// Get ; separated hash tags
				HashtagEntity[] hashtagEntities = status.getHashtagEntities();
				String hashtags = "";
				for (HashtagEntity hashtag : hashtagEntities) {
					hashtags += hashtag.getText() + ";";
				}
				sb.append("\"" + hashtags + "\",");
				sb.append("\"" + status.isRetweet() + "\",");
				sb.append("\"" + status.isRetweeted() + "\",");
				sb.append("\"" + (status.getPlace() == null ? "" : status.getPlace().getCountryCode()) + "\",");
				sb.append("\"" + status.getInReplyToScreenName() + "\",");
				sb.append("\"" + status.getLang() + "\",");
				sb.append("\"" + status.getCreatedAt().getTime() + "\",");
				sb.append("\"" + status.getFavoriteCount() + "\",");
				sb.append("\"" + status.getRetweetCount() + "\"");
				tweets.add(sb.toString());
				System.out.println(sb.toString());
				Thread.sleep(delay);
			}
			writeCsv(saveFilePath, tweets.toArray(new String[] {}), true);
			tweets = new ArrayList<String>();
		}
	}

	public List<String> readLines(String filePath) {
		ArrayList<String> content = new ArrayList<String>();
		BufferedReader in = null;
		try {
			in = new BufferedReader(new FileReader(filePath));
			String line = "";
			while((line = in.readLine()) != null) {
				content.add(line);
			}
			if (in != null) {
				in.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return content;
	}


	public void writeCsv(String filePath, String[] content, boolean append, boolean firstRowHeader) {
		File f = new File(filePath);
		if (firstRowHeader) {
			if (!f.exists()) {
				String header = content[1];
				writeCsv(filePath, new String[] { header }, append);
			}
			content = Arrays.copyOfRange(content, 1, content.length - 1);
		}
		writeCsv(filePath, content, append);
	}

	public void writeCsv(String filePath, String[] content, boolean append) {
		BufferedWriter out = null;
		try {
			out = new BufferedWriter(new FileWriter(filePath, append));
			for (int i = 0; i < content.length; i++) {
				String s = content[i] + "\n";
				out.write(s);
			}
			if (out != null) {
				out.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
