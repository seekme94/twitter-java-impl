
/**
 * 
 */
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import com.twitter.Constant;
import com.twitter.util.ClassLoaderUtil;
import com.twitter.util.FileUtil;
import com.twitter.util.TwitterUtil;

import twitter4j.TwitterException;
import twitter4j.User;

/**
 * @author owais.hussain@irdresearch.org
 */
public class TwitterMain {

	private static final Logger log = Logger.getLogger(TwitterMain.class);

	private static String consumerKey = "my_consumer_key";

	private static String consumerSecret = "my_consumer_secret";

	private static String consumerToken = "my_consumer_token";

	private static String consumerTokenSecret = "my_consumer_token_secret";

	private static String saveFilePath = "out/savefile.csv";

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
		TwitterUtil twitterUtil = new TwitterUtil(consumerKey, consumerSecret, consumerToken, consumerTokenSecret);
		List<String> screenNames = fileUtil.readLines(screenNamesFilePath);
		if (fetchFriends) {
			for (String name : screenNames) {
				try {
					User user = twitterUtil.getTwitter().showUser(name);
					log.info("Fetching friends of: " + user.getScreenName());
					List<String[]> content = twitterUtil.getFriendsInRaw(user);
					write(saveFilePath, content, appendFile);
				}
				catch (Exception e) {
					log.error(e.getMessage());
					Thread.sleep(Constant.DELAY);
				}
			}
		}
		if (fetchTweets) {
			for (String name : screenNames) {
				try {
					User user = twitterUtil.getTwitter().showUser(name);
					List<String[]> content = twitterUtil.getUserTimelineInRaw(user, 20, true);
					write(saveFilePath, content, true);
					Thread.sleep(Constant.DELAY);
				}
				catch (TwitterException e) {
					// In case of rate limit exception, delay for the time said
					if (e.getStatusCode() == 401) {
						int timeInSeconds = e.getRateLimitStatus().getSecondsUntilReset();
						Thread.sleep((timeInSeconds + Constant.DELAY));
					}
				}
				catch (Exception e) {
					log.error(name + " caused Exception: " + e.getCause());
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
