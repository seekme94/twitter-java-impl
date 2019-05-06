/**
 * 
 */
package com.twitter;


/**
 * @author owaishussain@outlook.com
 *
 */
public class Constant {
	
	private Constant() {
	}

	public static final String[] TWEET_HEADER = { "tweet_id", "user_id", "screen_name", "text", "hashtags", "retweet",
	        "retweeted", "location", "reply_to_screen_name", "lang", "timestamp", "fav_count", "rt_count" };

	public static final String[] USER_HEADER = { "user", "connection_id", "connection_name", "screen_name", "status_count",
	        "date_created" };

	public static final String DATE_FORMAT = "yyyy-MM-dd";

	public static final int DELAY = 2000;

}
