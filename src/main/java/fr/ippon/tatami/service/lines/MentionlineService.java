package fr.ippon.tatami.service.lines;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.ippon.tatami.domain.Tweet;
import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.exception.FunctionalException;
import fr.ippon.tatami.repository.FollowerRepository;
import fr.ippon.tatami.repository.MentionLineRepository;
import fr.ippon.tatami.repository.TimeLineRepository;
import fr.ippon.tatami.service.pipeline.tweet.TweetHandler;
import fr.ippon.tatami.service.util.TatamiConstants;

public class MentionlineService extends AbstractlineService implements TweetHandler
{
	private final Logger log = LoggerFactory.getLogger(MentionlineService.class);

	private static final Pattern USER_PATTERN = Pattern.compile(TatamiConstants.USER_REGEXP);

	private static final Pattern HTML_ENCODED_USER_PATTERN = Pattern.compile(TatamiConstants.HTML_ENCODED_USER_REGEXP);

	private TimeLineRepository timeLineRepository;

	private MentionLineRepository mentionLineRepository;

	private FollowerRepository followerRepository;

	@Override
	public void onTweetPost(Tweet tweet) throws FunctionalException
	{
		User currentUser = userService.getCurrentUser();

		Matcher usermatcher = USER_PATTERN.matcher(tweet.getContent());

		// Set to eliminate multiple quotes for same user in same tweet
		Set<String> quotedUsers = new HashSet<String>();
		while (usermatcher.find())
		{
			String quotedUser = usermatcher.group(1).toLowerCase();
			if (!quotedUsers.contains(quotedUser))
			{
				quotedUsers.add(quotedUser);

				Collection<String> userFollowers = this.followerRepository.findFollowersForUser(currentUser);
				if (!userFollowers.contains(quotedUser))
				{
					log.debug("Add tweet to quoted user " + quotedUser + " timeline");
					this.timeLineRepository.addTweetToTimeline(quotedUser, tweet.getTweetId());
					this.mentionLineRepository.addTweetToMentionline(quotedUser, tweet.getTweetId());
					this.mentionLineRepository.addTweetToIndex(tweet.getLogin(), quotedUser, tweet.getTweetId());
				}
			}

		}
	}

	@Override
	public void onTweetRemove(Tweet tweet) throws FunctionalException
	{
		if (!tweet.isNotification())
		{
			Matcher usermatcher = HTML_ENCODED_USER_PATTERN.matcher(tweet.getContent());
			// Set to eliminate multiple quotes for same user in same tweet
			Set<String> quotedUsers = new HashSet<String>();
			while (usermatcher.find())
			{
				String quotedUser = usermatcher.group(1).toLowerCase();
				if (!quotedUsers.contains(quotedUser))
				{
					quotedUsers.add(quotedUser);
					log.debug("Remove tweet from quoted user " + quotedUser + " timeline");
					this.timeLineRepository.removeTweetFromTimeline(quotedUser, tweet.getTweetId());
					this.mentionLineRepository.removeTweetFromMentionline(quotedUser, tweet.getTweetId());
					this.mentionLineRepository.removeTweetFromIndex(tweet.getLogin(), quotedUser, tweet.getTweetId());
				}
			}
		}

	}

	public void setTimeLineRepository(TimeLineRepository timeLineRepository)
	{
		this.timeLineRepository = timeLineRepository;
	}

	public void setFollowerRepository(FollowerRepository followerRepository)
	{
		this.followerRepository = followerRepository;
	}

	public void setMentionLineRepository(MentionLineRepository mentionLineRepository)
	{
		this.mentionLineRepository = mentionLineRepository;
	}

}
