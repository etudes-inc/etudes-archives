/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveJforumHandler.java $
 * $Id: ArchiveJforumHandler.java 8256 2014-06-13 20:11:14Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013, 2014 Etudes, Inc.
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.util.XrefHelper;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives archive handler for JForum
 */
public class ArchiveJforumHandler implements ArchiveHandler
{
	public class Attachment
	{
		String description;
		Long extensionId;
		String name;
		String path;
		Long size;
		Long time;
		String type;
	}

	public class Category
	{
		Boolean addToGradebook;
		Long displayOrder;
		Long endDate;
		Boolean gradable;
		Long id;
		Boolean lockOnEnd;
		Long minPosts;
		Boolean minPostsRequired;
		Boolean moderated;
		Float points;
		Long startDate;
		String title;
		Long allowUntilDate;
		Boolean hideUntilOpen;
	}

	public class Forum
	{
		Long accessType;
		Boolean addToGradebook;
		String description;
		Long endDate;
		Long gradeType;
		Long id;
		Boolean lockOnEnd;
		Long minPosts;
		Boolean minPostsRequired;
		Boolean moderated;
		String name;
		Float points;
		Long seq;
		Long startDate;
		Long type;
		Long allowUntilDate;
		Boolean hideUntilOpen;
		Integer forumTopicOrder;
		Boolean forumTopicLikes;
		Integer groupReadonlyAccess;
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
		Boolean addToGradebook;
		Long endDate;
		Long firstPostId;
		Long forumId;
		Boolean grade;
		Long id;
		Boolean lockOnEnd;
		Long minPosts;
		Boolean minPostsRequired;
		Boolean moderated;
		Float points;
		Long startDate;
		Long status;
		Long time;
		String title;
		Long type;
		String userId;
		Long allowUntilDate;
		Boolean hideUntilOpen;
	}

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveJforumHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.jforum.tool";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: The file system root to the attachment files. */
	protected String fileRoot = null;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * {@inheritDoc}
	 */
	public void archive(String siteId, Archive archive)
	{
		M_log.info("archive " + applicationId + " in site: " + siteId);

		// read the categories for the site
		List<Category> categories = readCategories(siteId);
		for (Category category : categories)
		{
			// make an artifact
			Artifact artifact = archive.newArtifact(applicationId, "/category/" + category.id.toString());
			artifact.getProperties().put("id", category.id);
			artifact.getProperties().put("title", category.title);
			artifact.getProperties().put("seq", category.displayOrder);
			artifact.getProperties().put("moderated", category.moderated);
			if (category.startDate != null) artifact.getProperties().put("startDate", category.startDate);
			if (category.endDate != null) artifact.getProperties().put("endDate", category.endDate);
			if (category.allowUntilDate != null) artifact.getProperties().put("allowUntilDate", category.allowUntilDate);
			artifact.getProperties().put("hideUntilOpen", category.hideUntilOpen);
			artifact.getProperties().put("lockOnEnd", category.lockOnEnd);
			artifact.getProperties().put("gradable", category.gradable);
			artifact.getProperties().put("addToGradebook", category.addToGradebook);
			artifact.getProperties().put("minPosts", category.minPosts);
			artifact.getProperties().put("minPostsRequired", category.minPostsRequired);
			artifact.getProperties().put("points", category.points);

			// archive it
			archive.archive(artifact);

			// forums
			List<Forum> forums = readForums(category.id);
			for (Forum forum : forums)
			{
				// make an artifact
				artifact = archive.newArtifact(applicationId, "/forum/" + forum.id.toString());
				artifact.getProperties().put("id", forum.id);
				artifact.getProperties().put("categoryId", category.id);
				artifact.getProperties().put("accessType", forum.accessType);
				artifact.getProperties().put("description", forum.description);
				if (forum.endDate != null) artifact.getProperties().put("endDate", forum.endDate);
				artifact.getProperties().put("gradeType", forum.gradeType);
				artifact.getProperties().put("moderated", forum.moderated);
				artifact.getProperties().put("name", forum.name);
				artifact.getProperties().put("seq", forum.seq);
				if (forum.startDate != null) artifact.getProperties().put("startDate", forum.startDate);
				if (forum.allowUntilDate != null) artifact.getProperties().put("allowUntilDate", forum.allowUntilDate);
				artifact.getProperties().put("hideUntilOpen", forum.hideUntilOpen);
				artifact.getProperties().put("forumTopicOrder", forum.forumTopicOrder);
				artifact.getProperties().put("type", forum.type);
				artifact.getProperties().put("lockOnEnd", forum.lockOnEnd);
				artifact.getProperties().put("addToGradebook", forum.addToGradebook);
				artifact.getProperties().put("minPosts", forum.minPosts);
				artifact.getProperties().put("minPostsRequired", forum.minPostsRequired);
				artifact.getProperties().put("points", forum.points);
				artifact.getProperties().put("forumTopicLikes", forum.forumTopicLikes);
				if (forum.groupReadonlyAccess != null) artifact.getProperties().put("groupReadonlyAccess", forum.groupReadonlyAccess);

				// get any groups and sections as group titles
				List<String> groupIds = readForumGroups(forum.id);
				List<String> groupTitles = new ArrayList<String>();
				List<String> sectionTitles = new ArrayList<String>();
				try
				{
					Site s = this.siteService.getSite(siteId);
					for (String gid : groupIds)
					{
						Group group = s.getGroup(gid);
						if (group != null)
						{
							// for groups
							if (group.getProperties().getProperty("sections_category") == null)
							{
								groupTitles.add(group.getTitle());
							}

							// for sections
							else
							{
								sectionTitles.add(group.getTitle());
							}
						}
					}
				}
				catch (IdUnusedException e)
				{
					M_log.warn("archive: missing site: " + siteId);
				}
				if (!groupTitles.isEmpty()) artifact.getProperties().put("groups", groupTitles);
				if (!sectionTitles.isEmpty()) artifact.getProperties().put("sections", sectionTitles);

				// archive it
				archive.archive(artifact);

				// topics
				List<Topic> topics = readTopics(forum.id);
				for (Topic topic : topics)
				{
					// make an artifact
					artifact = archive.newArtifact(applicationId, "/topic/" + topic.id.toString());
					artifact.getProperties().put("id", topic.id);
					artifact.getProperties().put("forumId", topic.forumId);
					artifact.getProperties().put("grade", topic.grade);
					artifact.getProperties().put("moderated", topic.moderated);
					artifact.getProperties().put("status", topic.status);
					artifact.getProperties().put("time", topic.time);
					artifact.getProperties().put("title", topic.title);
					artifact.getProperties().put("type", topic.type);
					artifact.getProperties().put("userId", topic.userId);
					if (topic.startDate != null) artifact.getProperties().put("startDate", topic.startDate);
					if (topic.endDate != null) artifact.getProperties().put("endDate", topic.endDate);
					if (topic.allowUntilDate != null) artifact.getProperties().put("allowUntilDate", topic.allowUntilDate);
					artifact.getProperties().put("hideUntilOpen", topic.hideUntilOpen);
					artifact.getProperties().put("lockOnEnd", topic.lockOnEnd);
					artifact.getProperties().put("addToGradebook", topic.addToGradebook);
					artifact.getProperties().put("minPosts", topic.minPosts);
					artifact.getProperties().put("minPostsRequired", topic.minPostsRequired);
					artifact.getProperties().put("points", topic.points);

					try
					{
						artifact.getProperties().put("user", this.userDirectoryService.getUser(topic.userId).getDisplayName());
					}
					catch (UserNotDefinedException e)
					{
					}

					Post post = readTopicPost(topic.firstPostId);
					if (post != null)
					{
						artifact.getProperties().put("text", post.text);
						artifact.getProperties().put("enableHtml", post.enableHtml);
						artifact.getProperties().put("enableSig", post.enableSig);

						// record resource references in the text
						Set<String> refs = XrefHelper.harvestEmbeddedReferences(post.text, null);
						artifact.getReferences().addAll(refs);

						// attachments
						if (post.hasAttachments)
						{
							List<Attachment> attachments = this.readAttachments(post.id);
							Collection<Map<String, Object>> attachmentsCollection = new ArrayList<Map<String, Object>>();
							for (Attachment attachment : attachments)
							{
								Map<String, Object> attachmentMap = new HashMap<String, Object>();
								attachmentsCollection.add(attachmentMap);

								attachmentMap.put("description", attachment.description);
								attachmentMap.put("extensionId", attachment.extensionId);
								attachmentMap.put("name", attachment.name);
								attachmentMap.put("size", attachment.size);
								attachmentMap.put("time", attachment.time);
								attachmentMap.put("type", attachment.type);
								attachmentMap.put("body", streamAttachment(attachment.path));
							}
							artifact.getProperties().put("attachments", attachmentsCollection);
						}
					}

					// archive it
					archive.archive(artifact);
				}
			}
		}
	}

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterArchiveHandler(this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public String getApplicationId()
	{
		return applicationId;
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerArchiveHandler(this);

		// check for a configuration for the JForum file root
		String root = this.serverConfigurationService.getString("etudes.jforum.attachments.store.dir");
		if (root != null)
		{
			if (!root.endsWith("/")) root = root + "/";
			this.setFileRoot(root);
		}

		M_log.info("init(): fileRoot: " + this.fileRoot);
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
	 * Set the file system root for attachment files for JForum.
	 * 
	 * @param root
	 *        The file system root.
	 */
	public void setFileRoot(String root)
	{
		this.fileRoot = root;
	}

	/**
	 * Dependency: ServerConfigurationService.
	 * 
	 * @param service
	 *        The ServerConfigurationService.
	 */
	public void setServerConfigurationService(ServerConfigurationService service)
	{
		this.serverConfigurationService = service;
	}

	/**
	 * Dependency: SiteService.
	 * 
	 * @param service
	 *        The SiteService.
	 */
	public void setSiteService(SiteService service)
	{
		this.siteService = service;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Set the UserDirectoryService.
	 * 
	 * @param service
	 *        The UserDirectoryService.
	 */
	public void setUserDirectoryService(UserDirectoryService service)
	{
		this.userDirectoryService = service;
	}

	/**
	 * Make a float from a possibly null string.
	 * 
	 * @param str
	 *        The string.
	 * @return The long.
	 */
	protected Float floatValue(String str)
	{
		if (str == null) return null;
		try
		{
			return Float.valueOf(str);
		}
		catch (NumberFormatException e)
		{
			return null;
		}
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
	 * Read topics for this forum; only those marked for export.
	 * 
	 * @param forumId
	 *        The forum Id
	 * @return List<Topic> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Attachment> readAttachments(final Long postId)
	{
		String sql = "SELECT D.PHYSICAL_FILENAME, D.REAL_FILENAME, D.DESCRIPTION, D.MIMETYPE, D.FILESIZE, D.UPLOAD_TIME, D.EXTENSION_ID"
				+ " FROM JFORUM_ATTACH A                                                                                               "
				+ " INNER JOIN JFORUM_ATTACH_DESC D ON A.ATTACH_ID = D.ATTACH_ID                                                       "
				+ " WHERE A.POST_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = postId;
		final SqlService ss = this.sqlService;

		List<Attachment> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Attachment a = new Attachment();
					a.path = StringUtil.trimToNull(result.getString(1));
					a.name = StringUtil.trimToNull(result.getString(2));
					a.description = StringUtil.trimToNull(result.getString(3));
					a.type = StringUtil.trimToNull(result.getString(4));
					a.size = longValue(StringUtil.trimToNull(result.getString(5)));
					Timestamp ts = result.getTimestamp(6, ss.getCal());
					a.time = (ts == null) ? null : Long.valueOf(ts.getTime());
					a.extensionId = longValue(StringUtil.trimToNull(result.getString(7)));

					return a;
				}
				catch (SQLException e)
				{
					M_log.warn("readAttachments: " + e.toString());
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
	 * Read categories for this site.
	 * 
	 * @param siteId
	 *        The context site Id
	 * @return List<Category> for the categories found in the site.
	 */
	@SuppressWarnings("unchecked")
	protected List<Category> readCategories(final String siteId)
	{
		String sql = "SELECT C.CATEGORIES_ID, C.TITLE, C.DISPLAY_ORDER, C.MODERATED, C.START_DATE, C.END_DATE, C.LOCK_END_DATE,       "
				+ " C.GRADABLE, C.ALLOW_UNTIL_DATE, C.HIDE_UNTIL_OPEN, G.ADD_TO_GRADEBOOK, G.MIN_POSTS, G.MIN_POSTS_REQUIRED, G.POINTS"
				+ " FROM JFORUM_CATEGORIES C                                                                                          "
				+ " LEFT OUTER JOIN JFORUM_GRADE G ON G.FORUM_ID = 0 AND G.TOPIC_ID = 0 AND C.CATEGORIES_ID = G.CATEGORIES_ID         "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_CATEGORIES CC ON C.CATEGORIES_ID = CC.CATEGORIES_ID                                "
				+ " WHERE CC.COURSE_ID = ? AND C.ARCHIVED = 0 ORDER BY C.DISPLAY_ORDER ASC";
		Object[] fields = new Object[1];
		fields[0] = siteId;
		final SqlService ss = this.sqlService;

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
					Timestamp ts = result.getTimestamp(5, ss.getCal());
					c.startDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(6, ss.getCal());
					c.endDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					c.lockOnEnd = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(7))));
					c.gradable = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(8))));
					ts = result.getTimestamp(9, ss.getCal());
					c.allowUntilDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					c.hideUntilOpen = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(10))));
					c.addToGradebook = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(11))));
					c.minPosts = longValue(StringUtil.trimToNull(result.getString(12)));
					c.minPostsRequired = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(13))));
					c.points = floatValue(StringUtil.trimToNull(result.getString(14)));

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
	 * Read group ids for this forum.
	 * 
	 * @param forumId
	 *        The forum Id
	 * @return List<String> of group ids for the forum.
	 */
	@SuppressWarnings("unchecked")
	protected List<String> readForumGroups(final Long forumId)
	{
		String sql = "SELECT SAKAI_GROUP_ID FROM JFORUM_FORUM_SAKAI_GROUPS WHERE FORUM_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = forumId;

		List<String> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String rv = result.getString(1);
					return rv;
				}
				catch (SQLException e)
				{
					M_log.warn("readForumGroups: " + e.toString());
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
	 *        The category Id
	 * @return List<Forum> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Forum> readForums(final Long categoryId)
	{
		String sql = "SELECT F.FORUM_ID, F.FORUM_NAME, F.FORUM_DESC, F.FORUM_ORDER, F.MODERATED, F.START_DATE, F.END_DATE,"
				+ " F.FORUM_TYPE, F.FORUM_ACCESS_TYPE, F.FORUM_GRADE_TYPE, F.LOCK_END_DATE, F.ALLOW_UNTIL_DATE, F.HIDE_UNTIL_OPEN, F.FORUM_TOPIC_ORDER, F.FORUM_TOPIC_LIKES,"
				+ " G.ADD_TO_GRADEBOOK, G.MIN_POSTS, G.MIN_POSTS_REQUIRED, G.POINTS, R.ACCESS_TYPE"
				+ " FROM JFORUM_FORUMS F LEFT OUTER JOIN JFORUM_GRADE G ON F.FORUM_ID = G.FORUM_ID AND G.TOPIC_ID = 0"
				+ " LEFT OUTER JOIN JFORUM_FORUM_SAKAI_GROUPS_READONLY_ACCESS R ON F.FORUM_ID = R.FORUM_ID" + " WHERE F.CATEGORIES_ID = ?"
				+ " ORDER BY F.FORUM_ORDER ASC";
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
					f.lockOnEnd = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(11))));
					ts = result.getTimestamp(12, ss.getCal());
					f.allowUntilDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					f.hideUntilOpen = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(13))));
					f.forumTopicOrder = Integer.valueOf(result.getString(14));
					f.forumTopicLikes = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(15))));
					f.addToGradebook = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(16))));
					f.minPosts = longValue(StringUtil.trimToNull(result.getString(17)));
					f.minPostsRequired = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(18))));
					f.points = floatValue(StringUtil.trimToNull(result.getString(19)));

					String groupReadonlyAccess = result.getString(20);
					if (groupReadonlyAccess == null)
					{
						f.groupReadonlyAccess = null;
					}
					else
					{
						f.groupReadonlyAccess = Integer.valueOf(groupReadonlyAccess);
					}
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
	 * Read topic's initial post
	 * 
	 * @param postId
	 *        The post Id
	 * @return List<Topic> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected Post readTopicPost(final Long postId)
	{
		String sql = "SELECT T.POST_TEXT, P.ENABLE_HTML, P.ENABLE_SIG, P.ATTACH, P.POST_ID                                   "
				+ " FROM JFORUM_POSTS P                                                                                      "
				+ " LEFT OUTER JOIN JFORUM_POSTS_TEXT T ON P.POST_ID = T.POST_ID                                             "
				+ " WHERE P.POST_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = postId;

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

		if (rv.isEmpty()) return null;
		return rv.get(0);
	}

	/**
	 * Read topics for this forum; only those marked for export.
	 * 
	 * @param forumId
	 *        The forum Id
	 * @return List<Topic> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Topic> readTopics(final Long forumId)
	{
		String sql = "SELECT T.TOPIC_ID, T.FORUM_ID, T.TOPIC_TITLE, T.TOPIC_TIME, T.MODERATED, T.TOPIC_GRADE, T.TOPIC_STATUS, T.TOPIC_TYPE,  "
				+ " U.SAKAI_USER_ID, T.TOPIC_FIRST_POST_ID, T.START_DATE, T.END_DATE, T.LOCK_END_DATE, T.ALLOW_UNTIL_DATE, T.HIDE_UNTIL_OPEN,"
				+ " G.ADD_TO_GRADEBOOK, G.MIN_POSTS, G.MIN_POSTS_REQUIRED, G.POINTS                                                          "
				+ " FROM JFORUM_TOPICS T                                                                                                     "
				+ " LEFT OUTER JOIN JFORUM_USERS U ON T.USER_ID = U.USER_ID                                                                  "
				+ " LEFT OUTER JOIN JFORUM_GRADE G ON T.FORUM_ID = G.FORUM_ID AND T.TOPIC_ID = G.TOPIC_ID                                    "
				+ " WHERE T.FORUM_ID = ? AND T.TOPIC_EXPORT = 1";
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
					ts = result.getTimestamp(11, ss.getCal());
					t.startDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(12, ss.getCal());
					t.endDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					t.lockOnEnd = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(13))));
					ts = result.getTimestamp(14, ss.getCal());
					t.allowUntilDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					t.hideUntilOpen = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(15))));
					t.addToGradebook = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(16))));
					t.minPosts = longValue(StringUtil.trimToNull(result.getString(17)));
					t.minPostsRequired = Boolean.valueOf("1".equals(StringUtil.trimToNull(result.getString(18))));
					t.points = floatValue(StringUtil.trimToNull(result.getString(19)));

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
	 * Get a stream on the attachment.
	 * 
	 * @param path
	 *        The relative file name
	 * @return The input stream, or null if not found.
	 */
	protected InputStream streamAttachment(String path)
	{
		// form the file name
		String name = this.fileRoot + path;
		File file = new File(name);

		// read
		try
		{
			FileInputStream in = new FileInputStream(file);
			return in;
		}
		catch (FileNotFoundException e)
		{
			M_log.warn("streamAttachment: " + e);
			return null;
		}
	}
}
