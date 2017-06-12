/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/XrefJforumHandler.java $
 * $Id: XrefJforumHandler.java 2857 2012-04-23 23:37:39Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012 Etudes, Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **********************************************************************************/

package org.etudes.archives.plugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.XrefHandler;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.util.StringUtil;

/**
 * Archives cross reference handler for JForum
 */
public class XrefJforumHandler implements XrefHandler
{
	public class Category
	{
		Long displayOrder;

		Long id;

		Boolean moderated;

		String title;
	}

	public class Forum
	{
		Long accessType;

		String description;

		Long endDate;

		Long gradeType;

		Long id;

		Boolean moderated;

		String name;

		Long seq;

		Long startDate;

		Long type;
	}

	public class Post
	{
		Boolean enableHtml;

		Boolean enableSig;

		Boolean hasAttachments;

		Long id;

		String text;
	}

	public class Topic
	{
		Long firstPostId;

		Long forumId;

		Boolean grade;

		Long id;

		Boolean moderated;

		Long status;

		Long time;

		String title;

		Long type;

		String userId;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(XrefJforumHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.jforum.tool";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterXrefHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerXrefHandler(applicationId, this);
		M_log.info("init()");
	}

	/**
	 * {@inheritDoc}
	 */
	public int removeXref(String siteId)
	{
		// ignore user sites
		if (siteId.startsWith("~")) return 0;

		M_log.info("xrefs for " + applicationId + " in site: " + siteId);

		int count = 0;

		// read the categories for the site
		List<Category> categories = readCategories(siteId);
		for (Category category : categories)
		{
			// forums
			List<Forum> forums = readForums(category.id);
			for (Forum forum : forums)
			{
				// topics
				List<Topic> topics = readTopics(forum.id);
				for (Topic topic : topics)
				{
					List<Post> posts = readTopicPost(topic.id);
					for (Post post : posts)
					{
						Set<String> refs = XrefHelper.harvestEmbeddedReferences(post.text, siteId);
						count += refs.size();

						// copy them somewhere in the site
						List<Translation> translations = XrefHelper.importTranslateResources(refs, siteId, "JForum");

						// update text with the new locations; also shorten any full URLs to this server
						String newText = XrefHelper.translateEmbeddedReferencesAndShorten(post.text, translations, siteId, null);

						// update if changed
						if (StringUtil.different(post.text, newText))
						{
							post.text = newText;
							updatePost(post);
						}
					}
				}
			}
		}

		return count;
	}

	/**
	 * Set the archives service.
	 * 
	 * @param service
	 *        The archives service.
	 */
	public void setArchivesService(ArchivesService service)
	{
		this.archivesService = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Make a long from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The long.
	 */
	protected Long longValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Long.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
	}

	/**
	 * Read categories for this site.
	 * 
	 * @param siteId
	 *        The context site Id.
	 * @return List<Category> for the categories found in the site.
	 */
	@SuppressWarnings("unchecked")
	protected List<Category> readCategories(final String siteId)
	{
		String sql = "SELECT C.CATEGORIES_ID, C.TITLE, C.DISPLAY_ORDER, C.MODERATED                                               "
				+ " FROM JFORUM_CATEGORIES C                                                                                      "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_CATEGORIES CC ON C.CATEGORIES_ID = CC.CATEGORIES_ID                            "
				+ " WHERE CC.COURSE_ID = ? AND C.ARCHIVED = 0";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Category> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Category c = new Category();
					c.id = longValue(StringUtil.trimToNull(result.getString(1)));
					c.title = StringUtil.trimToNull(result.getString(2));
					c.displayOrder = longValue(StringUtil.trimToNull(result.getString(3)));
					c.moderated = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(4))));

					return c;
				}
				catch (SQLException e)
				{
					M_log.warn("readCategories: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Read forums for this category.
	 * 
	 * @param categoryId
	 *        The category Id.
	 * @return List<Forum> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Forum> readForums(final Long categoryId)
	{
		String sql = "SELECT F.FORUM_ID, F.FORUM_NAME, F.FORUM_DESC, F.FORUM_ORDER, F.MODERATED, F.START_DATE, F.END_DATE,"
				+ " F.FORUM_TYPE, F.FORUM_ACCESS_TYPE, F.FORUM_GRADE_TYPE                                                 "
				+ " FROM JFORUM_FORUMS F WHERE F.CATEGORIES_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = categoryId;
		final SqlService ss = this.sqlService;

		List<Forum> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Forum f = new Forum();
					f.id = longValue(StringUtil.trimToNull(result.getString(1)));
					f.name = StringUtil.trimToNull(result.getString(2));
					f.description = StringUtil.trimToNull(result.getString(3));
					f.seq = longValue(StringUtil.trimToNull(result.getString(4)));
					f.moderated = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(5))));
					Timestamp ts = result.getTimestamp(6, ss.getCal());
					f.startDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(7, ss.getCal());
					f.endDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					f.type = longValue(StringUtil.trimToNull(result.getString(8)));
					f.accessType = longValue(StringUtil.trimToNull(result.getString(9)));
					f.gradeType = longValue(StringUtil.trimToNull(result.getString(10)));

					return f;
				}
				catch (SQLException e)
				{
					M_log.warn("readForums: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Read topic's posts.
	 * 
	 * @param topicId
	 *        The topic Id.
	 * @return List<Post> for the posts found for this topic.
	 */
	@SuppressWarnings("unchecked")
	protected List<Post> readTopicPost(final Long topicId)
	{
		String sql = "SELECT T.POST_TEXT, P.ENABLE_HTML, P.ENABLE_SIG, P.ATTACH, P.POST_ID                                   "
				+ " FROM JFORUM_POSTS P                                                                                      "
				+ " LEFT OUTER JOIN JFORUM_POSTS_TEXT T ON P.POST_ID = T.POST_ID                                             "
				+ " WHERE P.TOPIC_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = topicId;

		List<Post> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Post p = new Post();
					p.text = StringUtil.trimToNull(result.getString(1));
					p.enableHtml = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(2))));
					p.enableSig = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(3))));
					p.hasAttachments = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(4))));
					p.id = longValue(StringUtil.trimToNull(result.getString(5)));

					return p;
				}
				catch (SQLException e)
				{
					M_log.warn("readTopicPost: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Read all topics for this forum.
	 * 
	 * @param forumId
	 *        The forum Id
	 * @return List<Topic> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Topic> readTopics(final Long forumId)
	{
		String sql = "SELECT T.TOPIC_ID, T.FORUM_ID, T.TOPIC_TITLE, T.TOPIC_TIME, T.MODERATED, T.TOPIC_GRADE, T.TOPIC_STATUS, T.TOPIC_TYPE,"
				+ " U.SAKAI_USER_ID, T.TOPIC_FIRST_POST_ID                                                                                 "
				+ " FROM JFORUM_TOPICS T                                                                                                   "
				+ " LEFT OUTER JOIN JFORUM_USERS U ON T.USER_ID = U.USER_ID                                                                "
				+ " WHERE T.FORUM_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = forumId;
		final SqlService ss = this.sqlService;

		List<Topic> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Topic t = new Topic();
					t.id = longValue(StringUtil.trimToNull(result.getString(1)));
					t.forumId = longValue(StringUtil.trimToNull(result.getString(2)));
					t.title = StringUtil.trimToNull(result.getString(3));
					Timestamp ts = result.getTimestamp(4, ss.getCal());
					t.time = (ts == null) ? null : Long.valueOf(ts.getTime());
					t.moderated = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(5))));
					t.grade = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(6))));
					t.status = longValue(StringUtil.trimToNull(result.getString(7)));
					t.type = longValue(StringUtil.trimToNull(result.getString(8)));
					t.userId = StringUtil.trimToNull(result.getString(9));
					t.firstPostId = longValue(StringUtil.trimToNull(result.getString(10)));

					return t;
				}
				catch (SQLException e)
				{
					M_log.warn("readTopics: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		return rv;
	}

	/**
	 * Update the text of a post.
	 * 
	 * @param post
	 *        The post.
	 */
	protected void updatePost(final Post post)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				updatePostTx(post);
			}
		}, "insertPost");
	}

	/**
	 * Insert a new post (transaction code).
	 * 
	 * @param post
	 *        The post.
	 */
	protected void updatePostTx(Post post)
	{
		String sql = "UPDATE JFORUM_POSTS_TEXT SET POST_TEXT = ? WHERE POST_ID = ?";
		Object[] fields = new Object[2];
		int i = 0;
		fields[i++] = post.text;
		fields[i++] = post.id;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("updatePostTx: dbInsert failed");
		}
	}

	/**
	 * Do a write query.
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void write(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				writeTx(query, fields);
			}
		}, "write: " + fields[0] + " " + query);
	}

	/**
	 * Do a write query (transaction code)
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void writeTx(String query, Object[] fields)
	{
		if (!this.sqlService.dbWrite(query, fields))
		{
			throw new RuntimeException("writeTx: db write failed: " + fields[0] + " " + query);
		}
	}
}
