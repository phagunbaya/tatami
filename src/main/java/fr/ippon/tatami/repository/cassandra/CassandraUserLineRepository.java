package fr.ippon.tatami.repository.cassandra;

import static fr.ippon.tatami.config.ColumnFamilyKeys.USERLINE_CF;
import static me.prettyprint.hector.api.factory.HFactory.createSliceQuery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.inject.Inject;

import me.prettyprint.cassandra.model.CqlQuery;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hom.EntityManagerImpl;

import org.springframework.stereotype.Repository;

import fr.ippon.tatami.domain.User;
import fr.ippon.tatami.repository.UserLineRepository;
import fr.ippon.tatami.service.util.TatamiConstants;

@Repository
public class CassandraUserLineRepository extends CassandraAbstractRepository implements UserLineRepository
{
	@Inject
	private Keyspace keyspaceOperator;

	@Inject
	private EntityManagerImpl em;

	@Override
	public void addTweetToUserline(User user, String tweetId)
	{

		CqlQuery<String, Long, String> cqlQuery = new CqlQuery<String, Long, String>(keyspaceOperator, se, le, se);
		cqlQuery.setQuery("INSERT INTO UserLine(KEY,'" + user.getTweetCount() + "') VALUES('" + user.getLogin() + "','" + tweetId + "')");
		cqlQuery.execute();

		user.incrementTweetCount();
		em.persist(user);
	}

	@Override
	public Collection<String> getTweetsFromUserline(User user)
	{
		long endTweetColumn = user.getTweetCount() - 1;
		long startTweetColumn = user.getTweetCount() - TatamiConstants.DEFAULT_TWEET_LIST_SIZE - 1;

		List<HColumn<Long, String>> columns = createSliceQuery(keyspaceOperator, se, le, se).setColumnFamily(USERLINE_CF).setKey(user.getLogin())
				.setRange(endTweetColumn, startTweetColumn, true, TatamiConstants.DEFAULT_TWEET_LIST_SIZE).execute().get().getColumns();

		List<String> tweetIds = new ArrayList<String>();
		for (HColumn<Long, String> column : columns)
		{
			tweetIds.add(column.getValue());
		}

		return tweetIds;
	}

	@Override
	public Collection<String> getTweetsRangeFromUserline(User user, int start, int end)
	{
		List<String> tweetIds = new ArrayList<String>();

		long maxTweetColumn = user.getTweetCount() - 1;
		long endTweetColumn = maxTweetColumn - start + 1;
		long startTweetColumn = maxTweetColumn - end + 1;

		List<HColumn<Long, String>> columns = createSliceQuery(keyspaceOperator, se, le, se).setColumnFamily(USERLINE_CF).setKey(user.getLogin())
				.setRange(endTweetColumn, startTweetColumn, true, end - start + 1).execute().get().getColumns();

		for (HColumn<Long, String> column : columns)
		{
			tweetIds.add(column.getValue());
		}
		return tweetIds;
	}
}
