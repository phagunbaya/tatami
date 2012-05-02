package fr.ippon.tatami.service.user;

import static fr.ippon.tatami.service.util.TatamiConstants.USERTAG;
import static fr.ippon.tatami.service.util.TatamiConstants.USER_SEARCH_LIMIT;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.owasp.esapi.reference.DefaultEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.ippon.tatami.domain.Tweet;
import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.exception.FunctionalException;
import fr.ippon.tatami.repository.FollowerRepository;
import fr.ippon.tatami.repository.FriendRepository;
import fr.ippon.tatami.repository.TimeLineRepository;
import fr.ippon.tatami.repository.TweetRepository;
import fr.ippon.tatami.repository.UserIndexRepository;
import fr.ippon.tatami.repository.UserRepository;
import fr.ippon.tatami.service.security.AuthenticationService;
import fr.ippon.tatami.service.util.GravatarUtil;
import fr.ippon.tatami.service.util.TatamiConstants;

/**
 * @author Julien Dubois
 * @author DuyHai DOAN
 */

public class UserService
{

	private final Logger log = LoggerFactory.getLogger(UserService.class);

	private UserRepository userRepository;

	private UserIndexRepository userIndexRepository;

	private FollowerRepository followerRepository;

	private FriendRepository friendRepository;

	private AuthenticationService authenticationService;

	private TimeLineRepository timelineRepository;

	TweetRepository tweetRepository;

	public User getUserByLogin(String login) throws FunctionalException
	{
		log.debug("Looking for user with login : {} ", login);

		User user = userRepository.findUserByLogin(login);
		if (user == null)
		{
			throw new FunctionalException("No user found for login '" + login + "'");
		}
		return user;
	}

	public void updateUser(User updatedUser)

	{
		User currentUser = authenticationService.getCurrentUser();
		if (currentUser.getLogin().equals(updatedUser.getLogin()))
		{
			if (!StringUtils.equalsIgnoreCase(updatedUser.getFirstName(), currentUser.getFirstName()))
			{
				this.userIndexRepository.removeFirstName(currentUser.getFirstName().toLowerCase(), currentUser.getLogin().toLowerCase());
				this.userIndexRepository.insertFirstName(updatedUser.getFirstName().toLowerCase(), currentUser.getLogin().toLowerCase());
			}

			if (!StringUtils.equalsIgnoreCase(updatedUser.getLastName(), currentUser.getLastName()))
			{
				this.userIndexRepository.removeLastName(currentUser.getLastName().toLowerCase(), currentUser.getLogin().toLowerCase());
				this.userIndexRepository.insertLastName(updatedUser.getLastName().toLowerCase(), currentUser.getLogin().toLowerCase());
			}

			currentUser.setEmail(updatedUser.getEmail());
			currentUser.setGravatar(GravatarUtil.getHash(updatedUser.getEmail()));
			currentUser.setFirstName(updatedUser.getFirstName());
			currentUser.setLastName(updatedUser.getLastName());

			// XSS protection by encoding input data with ESAPI api
			currentUser.setBiography(DefaultEncoder.getInstance().encodeForHTML(updatedUser.getBiography()));
			currentUser.setLocation(DefaultEncoder.getInstance().encodeForHTML(updatedUser.getLocation()));
			currentUser.setWebsite(updatedUser.getWebsite());

			userRepository.updateUser(currentUser);

		}
		else
		{
			log.info("Security alert : user {} tried to update user {} ", currentUser.getLogin(), updatedUser);
		}
	}

	public void updateRandomUser(User user)
	{
		userRepository.updateUser(user);
	}

	public void createUser(User user)
	{
		user.setGravatar(GravatarUtil.getHash(user.getEmail()));
		userRepository.createUser(user);

		// Add user to user index
		userIndexRepository.insertLogin(user.getLogin().toLowerCase());
		userIndexRepository.insertFirstName(user.getFirstName().toLowerCase(), user.getLogin().toLowerCase());
		if (StringUtils.isNotBlank(user.getLastName()))
		{
			userIndexRepository.insertLastName(user.getLastName().toLowerCase(), user.getLogin().toLowerCase());
		}

	}

	public void followUser(String loginToFollow) throws FunctionalException
	{
		log.debug("Adding friend : {}", loginToFollow);

		User currentUser = authenticationService.getCurrentUser();
		Collection<String> friends = this.friendRepository.findFriendsForUser(currentUser);
		User followedUser = getUserByLogin(loginToFollow);

		if (followedUser != null && !followedUser.equals(currentUser))
		{
			if (!friends.contains(followedUser.getLogin()))
			{
				friendRepository.addFriend(currentUser, followedUser);
				followerRepository.addFollower(followedUser, currentUser);

				// Tweet alert
				String content = USERTAG + currentUser.getLogin() + " <strong>is now following you</strong>";
				Tweet alertTweet = tweetRepository.createTweet(currentUser.getLogin(), content); // removable
				timelineRepository.addTweetToTimeline(followedUser, alertTweet.getTweetId());

			}
		}
		else
		{
			log.debug("Followed user does not exist : {} ", loginToFollow);
		}
	}

	public void forgetUser(String login) throws FunctionalException
	{
		log.debug("Removing followed user : {} ", login);

		User currentUser = authenticationService.getCurrentUser();
		Collection<String> friends = this.friendRepository.findFriendsForUser(currentUser);

		User followedUser = getUserByLogin(login);

		if (followedUser != null)
		{
			if (friends.contains(followedUser.getLogin()))
			{
				friendRepository.removeFriend(currentUser, followedUser);
				followerRepository.removeFollower(followedUser, currentUser);

			}
		}
		else
		{
			log.debug("Followed user does not exist : {}", login);
		}
	}

	public Collection<String> getFriendsForUser(String login)
	{
		log.debug("Retrieving followed users : {}", login);

		User user = userRepository.findUserByLogin(login);

		return friendRepository.findFriendsForUser(user);
	}

	public Collection<User> getFriendsForUser(String login, String startUser, int count)
	{

		if (startUser == null && count < TatamiConstants.DEFAULT_USER_LIST_SIZE)
		{
			count = TatamiConstants.DEFAULT_USER_LIST_SIZE;
		}

		log.debug("Retrieving followed users : {} within range {}", login, startUser + " - " + count);

		User currentUser = authenticationService.getCurrentUser();
		User user = userRepository.findUserByLogin(login);

		return this.buildUserList(currentUser, friendRepository.findFriendsForUser(user, startUser, count));
	}

	public Collection<String> getFollowersForUser(String login)
	{
		log.debug("Retrieving following users : {}", login);

		User user = userRepository.findUserByLogin(login);

		return followerRepository.findFollowersForUser(user);
	}

	public Collection<User> getFollowersForUser(String login, String startUser, int count)
	{
		if (startUser == null && count < TatamiConstants.DEFAULT_USER_LIST_SIZE)
		{
			count = TatamiConstants.DEFAULT_USER_LIST_SIZE;
		}

		log.debug("Retrieving following users : {} within range {}", login, startUser + " - " + count);

		User currentUser = authenticationService.getCurrentUser();
		User user = userRepository.findUserByLogin(login);

		return this.buildUserList(currentUser, followerRepository.findFollowersForUser(user, startUser, count));
	}

	public List<User> findUser(String searchString)
	{
		return this.findUser(searchString, 1, USER_SEARCH_LIMIT);
	}

	public List<User> findUser(String searchString, int start, int end)
	{
		assert end > start : "User search end index should be greater than start index";
		assert start > 0 : "User search start index should be greater than 1";

		start--;

		List<String> logins = null;
		User currentUser = this.getCurrentUser();

		if (searchString.startsWith("@"))
		{
			logins = this.userIndexRepository.findLogin(searchString.substring(1).toLowerCase().trim(), end);
		}
		else
		{
			Set<String> set = new HashSet<String>();

			set.addAll(this.userIndexRepository.findLogin(searchString.toLowerCase().trim(), end));
			set.addAll(this.userIndexRepository.findFirstName(searchString.toLowerCase().trim(), end));
			set.addAll(this.userIndexRepository.findLastName(searchString.toLowerCase().trim(), end));

			logins = new ArrayList<String>(set);

			// Sort by logins
			Collections.sort(logins);

			// Extract search range
			if (start > logins.size())
			{
				logins = Arrays.asList();
			}
			else if (logins.size() > end)
			{
				logins = logins.subList(start, end);
			}
			else
			{
				logins = logins.subList(start, logins.size());
			}

		}
		return this.buildUserList(currentUser, logins);
	}

	public List<User> buildUserList(User currentUser, Collection<String> logins)
	{
		List<User> results = new ArrayList<User>();

		Collection<String> userFriends = this.friendRepository.findFriendsForUser(currentUser);

		User foundUser = null;
		for (String login : logins)
		{
			foundUser = this.userRepository.findUserByLogin(login);
			if (foundUser != null)
			{
				if (userFriends.contains(login))
				{
					foundUser.setFollow(false);
				}
				else
				{
					foundUser.setFollow(true);
				}
				results.add(foundUser);
			}
		}

		return results;
	}

	public User getCurrentUser()
	{
		return authenticationService.getCurrentUser();
	}

	public void setAuthenticationService(AuthenticationService authenticationService)
	{
		this.authenticationService = authenticationService;
	}

	public void setFollowerRepository(FollowerRepository followerRepository)
	{
		this.followerRepository = followerRepository;
	}

	public void setFriendRepository(FriendRepository friendRepository)
	{
		this.friendRepository = friendRepository;
	}

	public void setUserRepository(UserRepository userRepository)
	{
		this.userRepository = userRepository;
	}

	public void setUserIndexRepository(UserIndexRepository userIndexRepository)
	{
		this.userIndexRepository = userIndexRepository;
	}

	public void setTimelineRepository(TimeLineRepository timelineRepository)
	{
		this.timelineRepository = timelineRepository;
	}

	public void setTweetRepository(TweetRepository tweetRepository)
	{
		this.tweetRepository = tweetRepository;
	}

}