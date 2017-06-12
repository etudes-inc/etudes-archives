/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ImportJforumHandler.java $
 * $Id: ImportJforumHandler.java 8256 2014-06-13 20:11:14Z ggolden $
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
import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.util.TranslationImpl;
import org.etudes.util.XrefHelper;
import org.etudes.util.api.Translation;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.id.cover.IdManager;
import org.sakaiproject.service.gradebook.shared.AssignmentHasIllegalPointsException;
import org.sakaiproject.service.gradebook.shared.ConflictingAssignmentNameException;
import org.sakaiproject.service.gradebook.shared.ConflictingExternalIdException;
import org.sakaiproject.service.gradebook.shared.GradebookNotFoundException;
import org.sakaiproject.service.gradebook.shared.GradebookService;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.time.api.Time;
import org.sakaiproject.time.api.TimeService;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;

/**
 * Archives import handler for JForum
 */
public class ImportJforumHandler implements ImportHandler
{
	public class Attachment
	{
		String description;
		Long extensionId;
		Long id;
		String name;
		String path;
		Long size;
		Long time;
		String type;
	}

	/**
	 * The SqlService will write a null for a null or a blank String, but if we need a non-null blank string written, use one of these.
	 */
	public class BlankString
	{
		public String toString()
		{
			return "";
		}
	}

	public class Category
	{
		Boolean addToGradebook;
		Long allowUntilDate;
		Long displayOrder;
		Long endDate;
		Boolean gradable;
		Long gradeId;
		Boolean hideUntilOpen;
		Long id;
		Boolean lockOnEnd;
		Long minPosts;
		Boolean minPostsRequired;
		Boolean moderated;
		Float points;
		Long startDate;
		String title;
	}

	public class Forum
	{
		Long accessType;
		Boolean addToGradebook;
		Long allowUntilDate;
		String description;
		Long endDate;
		Boolean forumTopicLikes;
		Integer forumTopicOrder;
		Long gradeId;
		Long gradeType;
		Integer groupReadonlyAccess;
		Boolean hideUntilOpen;
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
	}

	public class Id
	{
		Long id;
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
		Long allowUntilDate;
		Long endDate;
		Long firstPostId;
		Long forumId;
		Boolean grade;
		Long gradeId;
		Boolean hideUntilOpen;
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
		Long userId;
	}

	/** The application Id. */
	protected final static String applicationId = "sakai.jforum.tool";

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ImportJforumHandler.class);

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: The file system root to the attachment files. */
	protected String fileRoot = null;

	/** Dependency: GradebookExternalAssessmentService */
	// for 2.4 only: protected GradebookExternalAssessmentService m_gradebookService = null;
	protected GradebookService gradebookService = null;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SessionManager */
	protected SessionManager sessionManager = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** Dependency: TimeService. */
	protected TimeService timeService = null;

	/** Dependency: UserDirectoryService. */
	protected UserDirectoryService userDirectoryService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterImportHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public void importArtifact(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		M_log.info("import " + applicationId + " in site: " + siteId);

		if (artifact.getReference().startsWith("/category/"))
		{
			importCategory(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/forum/"))
		{
			importForum(siteId, artifact, archive);
		}
		else if (artifact.getReference().startsWith("/topic/"))
		{
			importTopic(siteId, artifact, archive);
		}

		// notify JForum that we just imported and it needs to update its caches for the site
		Object[] fields = new Object[1];
		fields[0] = siteId;
		write("INSERT INTO JFORUM_IMPORT (SAKAI_SITE_ID, IMPORTED) VALUES (?,1) ON DUPLICATE KEY UPDATE IMPORTED = 1", fields);

		// also let JForum know that we have put forum content in the site, and it should not try to init it with the "seed" categories and forums
		setInited(siteId);
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerImportHandler(applicationId, this);

		// check for a configuration for the JForum file root
		String root = this.serverConfigurationService.getString("etudes.jforum.attachments.store.dir");
		if (root != null)
		{
			if (!root.endsWith("/")) root = root + "/";
			this.setFileRoot(root);
		}

		M_log.info("init(): jforum file root: " + this.fileRoot);
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerFilteredReferences(String siteId, Artifact artifact, Archive archive, Set<String> toolIds)
	{
		// import our data?
		if ((toolIds != null) && (!toolIds.contains(applicationId))) return;

		// if importing, add the references
		archive.getReferences().addAll(artifact.getReferences());
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
	 * Dependency: GradebookService.
	 * 
	 * @param service
	 *        The GradebookService.
	 */
	public void setGradebookService(/* for 2.4 only: GradebookExternalAssessmentService */GradebookService service)
	{
		this.gradebookService = service;
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
	 * Dependency: SessionManager.
	 * 
	 * @param service
	 *        The SessionManager.
	 */
	public void setSessionManager(SessionManager service)
	{
		this.sessionManager = service;
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
	 * Set the TimeService.
	 * 
	 * @param service
	 *        The TimeService.
	 */
	public void setTimeService(TimeService service)
	{
		this.timeService = service;
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

	protected void addToGradebook(String siteId, Long gradeRecordId, String title, double points, Date dueDate)
	{
		try
		{
			gradebookService.addExternalAssessment(siteId, "discussions-" + gradeRecordId.toString(), null, title, points, dueDate, "Discussions");
		}
		catch (GradebookNotFoundException e)
		{
		}
		catch (ConflictingAssignmentNameException e)
		{
		}
		catch (ConflictingExternalIdException e)
		{
		}
		catch (AssignmentHasIllegalPointsException e)
		{
		}
	}

	/**
	 * Compare two objects for differences, either may be null
	 * 
	 * @param a
	 *        One object.
	 * @param b
	 *        The other object.
	 * @return true if the object are different, false if they are the same.
	 */
	protected boolean different(Object a, Object b)
	{
		// if both null, they are the same
		if ((a == null) && (b == null)) return false;

		// if either are null (they both are not), they are different
		if ((a == null) || (b == null)) return true;

		// now we know neither are null, so compare
		return (!a.equals(b));
	}

	/**
	 * Find the JForum user id for this sakai user id
	 * 
	 * @param userId
	 *        The sakai user id Id
	 * @return JForum user id.
	 */
	@SuppressWarnings("unchecked")
	protected Long getJforumUser(final String userId)
	{
		String sql = "SELECT U.USER_ID FROM JFORUM_USERS U WHERE SAKAI_USER_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = userId;

		List<Long> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Long id = Long.valueOf(StringUtil.trimToNull(result.getString(1)));
					return id;
				}
				catch (SQLException e)
				{
					M_log.warn("getJforumUser: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});

		if (!rv.isEmpty()) return rv.get(0);

		// create a JForum user for the sakai user
		try
		{
			User user = this.userDirectoryService.getUser(userId);
			Long uid = insertUser(user);
			return uid;
		}
		catch (UserNotDefinedException e)
		{
			M_log.warn("getJforumUser: missing sakai user: " + userId);
			return null;
		}
	}

	/**
	 * Import a category from the artifact.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	protected void importCategory(String siteId, Artifact artifact, Archive archive)
	{
		// check for existing category
		List<Category> categories = readCategories(siteId);
		for (Category category : categories)
		{
			if (category.title.equalsIgnoreCase((String) artifact.getProperties().get("title")))
			{
				// map the category to the new id
				archive.getTranslations().add(
						new TranslationImpl("/jforum/category/" + artifact.getProperties().get("id"), "/jforum/category/" + category.id.toString()));

				return;
			}
		}

		Category category = new Category();
		category.title = (String) artifact.getProperties().get("title");
		category.moderated = (Boolean) artifact.getProperties().get("moderated");
		category.startDate = (Long) artifact.getProperties().get("startDate");
		category.endDate = (Long) artifact.getProperties().get("endDate");
		category.lockOnEnd = (Boolean) artifact.getProperties().get("lockOnEnd");
		category.gradable = (Boolean) artifact.getProperties().get("gradable");
		category.addToGradebook = (Boolean) artifact.getProperties().get("addToGradebook");
		category.minPosts = (Long) artifact.getProperties().get("minPosts");
		category.minPostsRequired = (Boolean) artifact.getProperties().get("minPostsRequired");
		category.points = (Float) artifact.getProperties().get("points");
		category.allowUntilDate = (Long) artifact.getProperties().get("allowUntilDate");
		category.hideUntilOpen = (Boolean) artifact.getProperties().get("hideUntilOpen");
		if (category.hideUntilOpen == null) category.hideUntilOpen = Boolean.TRUE;

		// display after existing ones
		category.displayOrder = Long.valueOf(categories.size() + 1);

		insertCategory(category, siteId);

		// if marked for grading, do a grade record
		if (category.gradable)
		{
			insertCategoryGrade(siteId, category);

			// send to gb if needed
			if (category.addToGradebook)
			{
				Date endDate = null;
				if (category.endDate != null)
				{
					endDate = new Date(category.endDate);
				}
				addToGradebook(siteId, category.gradeId, category.title, category.points.floatValue(), endDate);
			}
		}

		// map the category to the new id
		archive.getTranslations().add(
				new TranslationImpl("/jforum/category/" + artifact.getProperties().get("id"), "/jforum/category/" + category.id.toString()));
	}

	/**
	 * Import a forum from the artifact.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	@SuppressWarnings("unchecked")
	protected void importForum(String siteId, Artifact artifact, Archive archive)
	{
		// map the category id
		String categoryRef = "/jforum/category/" + ((Long) artifact.getProperties().get("categoryId")).toString();
		Long categoryId = null;
		for (Translation t : archive.getTranslations())
		{
			if (t.getFrom().equals(categoryRef))
			{
				categoryId = Long.valueOf(t.getTo().substring("/jforum/category/".length()));
				break;
			}
		}
		if (categoryId == null)
		{
			M_log.warn("importForum: missing category translation: categoryId: " + ((Long) artifact.getProperties().get("categoryId")).toString());
			return;
		}

		// check for existing forum
		List<Forum> forums = readForums(categoryId);
		for (Forum forum : forums)
		{
			if (forum.name.equalsIgnoreCase((String) artifact.getProperties().get("name")))
			{
				// map the forum to the new id
				archive.getTranslations().add(
						new TranslationImpl("/jforum/forum/" + artifact.getProperties().get("id"), "/jforum/forum/" + forum.id.toString()));

				return;
			}
		}

		Forum forum = new Forum();
		forum.accessType = (Long) artifact.getProperties().get("accessType");
		forum.description = (String) artifact.getProperties().get("description");
		forum.gradeType = (Long) artifact.getProperties().get("gradeType");
		forum.moderated = (Boolean) artifact.getProperties().get("moderated");
		forum.name = (String) artifact.getProperties().get("name");
		forum.type = (Long) artifact.getProperties().get("type");
		forum.startDate = (Long) artifact.getProperties().get("startDate");
		forum.endDate = (Long) artifact.getProperties().get("endDate");
		forum.lockOnEnd = (Boolean) artifact.getProperties().get("lockOnEnd");
		forum.addToGradebook = (Boolean) artifact.getProperties().get("addToGradebook");
		forum.minPosts = (Long) artifact.getProperties().get("minPosts");
		forum.minPostsRequired = (Boolean) artifact.getProperties().get("minPostsRequired");
		forum.points = (Float) artifact.getProperties().get("points");
		forum.allowUntilDate = (Long) artifact.getProperties().get("allowUntilDate");
		forum.hideUntilOpen = (Boolean) artifact.getProperties().get("hideUntilOpen");
		if (forum.hideUntilOpen == null) forum.hideUntilOpen = Boolean.TRUE;
		forum.forumTopicOrder = (Integer) artifact.getProperties().get("forumTopicOrder");
		if (forum.forumTopicOrder == null) forum.forumTopicOrder = 0;
		forum.forumTopicLikes = (Boolean) artifact.getProperties().get("forumTopicLikes");
		if (forum.forumTopicLikes == null) forum.forumTopicLikes = Boolean.FALSE;
		forum.groupReadonlyAccess = (Integer) artifact.getProperties().get("groupReadonlyAccess");

		// figure out groups
		List<String> forumGroupIds = new ArrayList<String>();
		List<String> groupTitles = (List<String>) artifact.getProperties().get("groups");
		List<String> sectionTitles = (List<String>) artifact.getProperties().get("sections");
		List<String> combined = new ArrayList<String>();
		if (groupTitles != null) combined.addAll(groupTitles);
		if (sectionTitles != null) combined.addAll(sectionTitles);
		if (!combined.isEmpty())
		{
			try
			{
				Site s = this.siteService.getSite(siteId);
				Collection<Group> groups = (Collection<Group>) s.getGroups();

				for (String groupTitle : combined)
				{
					for (Group g : groups)
					{
						if (g.getTitle().equals(groupTitle))
						{
							forumGroupIds.add(g.getId());
							break;
						}
					}
				}
			}
			catch (IdUnusedException e)
			{
				M_log.warn("importForum: missing site: " + siteId);
			}
		}

		// if there are no groups, make sure the accessType is set to "site" (0)
		if (forumGroupIds.isEmpty())
		{
			forum.accessType = Long.valueOf(0);
		}

		// order it after the others
		forum.seq = Long.valueOf(forums.size() + 1);

		insertForum(forum, categoryId);

		// if grade by forum, do a grade record
		if (forum.gradeType == 2)
		{
			insertForumGrade(siteId, forum);

			// send to gb if needed
			if (forum.addToGradebook)
			{
				Date endDate = null;
				if (forum.endDate != null)
				{
					endDate = new Date(forum.endDate);
				}
				addToGradebook(siteId, forum.gradeId, forum.name, forum.points.floatValue(), endDate);
			}
		}

		// set the groups, if any
		if (!forumGroupIds.isEmpty())
		{
			insertForumGroups(forum.id, forumGroupIds);

			// set the readonly access, if needed
			if (forum.groupReadonlyAccess != null)
			{
				insertForumGroupReadonlyAccess(forum.id, forum.groupReadonlyAccess);
			}
		}

		// map the forum to the new id
		archive.getTranslations().add(
				new TranslationImpl("/jforum/forum/" + artifact.getProperties().get("id"), "/jforum/forum/" + forum.id.toString()));
	}

	/**
	 * Import a topic from the artifact.
	 * 
	 * @param siteId
	 *        The site id.
	 * @param artifact
	 *        The artifact.
	 * @param archive
	 *        The archive.
	 */
	@SuppressWarnings("unchecked")
	protected void importTopic(String siteId, Artifact artifact, Archive archive)
	{
		// map the forum id
		String forumRef = "/jforum/forum/" + ((Long) artifact.getProperties().get("forumId")).toString();
		Long forumId = null;
		for (Translation t : archive.getTranslations())
		{
			if (t.getFrom().equals(forumRef))
			{
				forumId = Long.valueOf(t.getTo().substring("/jforum/forum/".length()));
				break;
			}
		}
		if (forumId == null)
		{
			M_log.warn("importTopic: missing forum translation: forumId: " + ((Long) artifact.getProperties().get("forumId")).toString());
			return;
		}

		// check for existing topic
		List<Topic> topics = readTopics(forumId);
		for (Topic topic : topics)
		{
			if (topic.title.equalsIgnoreCase((String) artifact.getProperties().get("title")))
			{
				return;
			}
		}

		Topic topic = new Topic();
		topic.forumId = forumId;
		topic.grade = (Boolean) artifact.getProperties().get("grade");
		topic.moderated = (Boolean) artifact.getProperties().get("moderated");
		topic.status = (Long) artifact.getProperties().get("status");
		topic.title = (String) artifact.getProperties().get("title");
		topic.type = (Long) artifact.getProperties().get("type");
		topic.startDate = (Long) artifact.getProperties().get("startDate");
		topic.endDate = (Long) artifact.getProperties().get("endDate");
		topic.lockOnEnd = (Boolean) artifact.getProperties().get("lockOnEnd");
		topic.addToGradebook = (Boolean) artifact.getProperties().get("addToGradebook");
		topic.minPosts = (Long) artifact.getProperties().get("minPosts");
		topic.minPostsRequired = (Boolean) artifact.getProperties().get("minPostsRequired");
		topic.points = (Float) artifact.getProperties().get("points");
		topic.allowUntilDate = (Long) artifact.getProperties().get("allowUntilDate");
		topic.hideUntilOpen = (Boolean) artifact.getProperties().get("hideUntilOpen");
		if (topic.hideUntilOpen == null) topic.hideUntilOpen = Boolean.TRUE;

		Long uid = getJforumUser(this.sessionManager.getCurrentSessionUserId());
		topic.userId = uid;

		insertTopic(topic);

		// if marked for grading, do a grade record
		if (topic.grade)
		{
			insertTopicGrade(siteId, topic);

			// send to gb if needed
			if (topic.addToGradebook)
			{
				Date endDate = null;
				if (topic.endDate != null)
				{
					endDate = new Date(topic.endDate);
				}
				addToGradebook(siteId, topic.gradeId, topic.title, topic.points.floatValue(), endDate);
			}
		}

		Post post = new Post();
		post.enableHtml = (Boolean) artifact.getProperties().get("enableHtml");
		post.enableSig = (Boolean) artifact.getProperties().get("enableSig");

		Collection<Map<String, Object>> attachmentMaps = (Collection<Map<String, Object>>) artifact.getProperties().get("attachments");
		if (attachmentMaps != null)
		{
			post.hasAttachments = Boolean.TRUE;
		}
		else
		{
			post.hasAttachments = Boolean.FALSE;
		}
		post.text = (String) artifact.getProperties().get("text");
		if (post.text != null) post.text = XrefHelper.translateEmbeddedReferences(post.text, archive.getTranslations(), null, null);

		insertPost(post, topic.id, forumId, uid, topic.title);

		if (attachmentMaps != null)
		{
			for (Map<String, Object> attachmentMap : attachmentMaps)
			{
				Attachment attachment = new Attachment();
				attachment.description = (String) attachmentMap.get("description");
				attachment.extensionId = (Long) attachmentMap.get("extensionId");
				attachment.name = (String) attachmentMap.get("name");
				attachment.size = (Long) attachmentMap.get("size");
				attachment.time = (Long) attachmentMap.get("time");
				attachment.type = (String) attachmentMap.get("type");

				byte[] body = archive.readFile((String) attachmentMap.get("body"), attachment.size.intValue());

				writeFile(attachment, body);
				insertAttachment(attachment, post, uid);
			}
		}
	}

	/**
	 * Insert a new post attachment.
	 * 
	 * @param forum
	 *        The forum.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertAttachment(final Attachment attachment, final Post post, final Long uid)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertAttachmentTx(attachment, post, uid);
			}
		}, "insertForum");
	}

	/**
	 * Insert a new post attachment (transaction code).
	 * 
	 * @param forum
	 *        The forum.
	 * @param categoryId
	 *        The category id.
	 */
	protected void insertAttachmentTx(Attachment attachment, Post post, Long uid)
	{
		String sql = "INSERT INTO JFORUM_ATTACH (POST_ID, USER_ID, PRIVMSGS_ID) VALUES(?,?,0)";

		Object[] fields = new Object[2];
		int i = 0;
		fields[i++] = post.id;
		fields[i++] = uid;

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "attach_id");
		if (id == null)
		{
			throw new RuntimeException("insertAttachmentTx: dbInsert failed");
		}

		// set the id
		attachment.id = id;

		sql = "INSERT INTO JFORUM_ATTACH_DESC (ATTACH_ID, PHYSICAL_FILENAME, REAL_FILENAME, DESCRIPTION, MIMETYPE, FILESIZE, UPLOAD_TIME, EXTENSION_ID)"
				+ " VALUES (?,?,?,?,?,?,?,?)";
		fields = new Object[8];
		i = 0;
		fields[i++] = attachment.id;
		fields[i++] = attachment.path;
		fields[i++] = attachment.name;
		fields[i++] = attachment.description == null ? new BlankString() : attachment.description;
		fields[i++] = attachment.type;
		fields[i++] = attachment.size;
		fields[i++] = this.timeService.newTime();
		fields[i++] = attachment.extensionId;

		id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "attach_desc_id");
		if (id == null)
		{
			throw new RuntimeException("insertAttachmentTx_desc: dbInsert failed");
		}
	}

	/**
	 * Insert a new category.
	 * 
	 * @param category
	 *        The category.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertCategory(final Category category, final String siteId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertCategoryTx(category, siteId);
			}
		}, "insertCategory");
	}

	/**
	 * Insert a new category grade record.
	 * 
	 * @param category
	 *        The category.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertCategoryGrade(final String siteId, final Category category)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertCategoryGradeTx(siteId, category);
			}
		}, "insertCategoryGrade");
	}

	/**
	 * Insert a new category grade record (transaction code).
	 * 
	 * @param siteId
	 *        The site id.
	 * @param category
	 *        The category.
	 */
	protected void insertCategoryGradeTx(String siteId, Category category)
	{
		String sql = "INSERT INTO JFORUM_GRADE (CONTEXT, GRADE_TYPE, FORUM_ID, TOPIC_ID, POINTS, ADD_TO_GRADEBOOK, CATEGORIES_ID, MIN_POSTS, MIN_POSTS_REQUIRED)"
				+ " VALUES(?,?,?,?,?,?,?,?,?)";

		Object[] fields = new Object[9];
		int i = 0;
		fields[i++] = siteId;
		fields[i++] = Long.valueOf(3); // grade by category
		fields[i++] = Long.valueOf(0);
		fields[i++] = Long.valueOf(0);
		fields[i++] = category.points;
		fields[i++] = category.addToGradebook ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = category.id;
		fields[i++] = category.minPosts;
		fields[i++] = category.minPostsRequired ? Integer.valueOf(1) : Integer.valueOf(0);

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "grade_id");
		if (id == null)
		{
			throw new RuntimeException("insertCategoryGradeTx: dbInsert failed");
		}
		category.gradeId = id;
	}

	/**
	 * Insert a new category (transaction code).
	 * 
	 * @param category
	 *        The category.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertCategoryTx(Category category, String siteId)
	{
		String sql = "INSERT INTO JFORUM_CATEGORIES (TITLE, MODERATED, DISPLAY_ORDER, START_DATE, END_DATE, LOCK_END_DATE, GRADABLE, ALLOW_UNTIL_DATE, HIDE_UNTIL_OPEN) VALUES(?,?,?,?,?,?,?,?,?)";

		Object[] fields = new Object[9];
		int i = 0;
		fields[i++] = category.title;
		fields[i++] = category.moderated ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = category.displayOrder;
		Time t = null;
		if (category.startDate != null) t = this.timeService.newTime(category.startDate);
		fields[i++] = t;
		t = null;
		if (category.endDate != null) t = this.timeService.newTime(category.endDate);
		fields[i++] = t;
		fields[i++] = category.lockOnEnd ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = category.gradable ? Integer.valueOf(1) : Integer.valueOf(0);
		t = null;
		if (category.allowUntilDate != null) t = this.timeService.newTime(category.allowUntilDate);
		fields[i++] = t;
		fields[i++] = category.hideUntilOpen ? Integer.valueOf(1) : Integer.valueOf(0);
		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "categories_id");
		if (id == null)
		{
			throw new RuntimeException("insertCategoryTx: dbInsert failed");
		}

		// set the id
		category.id = id;

		// add to the category list for the site
		sql = "INSERT INTO JFORUM_SAKAI_COURSE_CATEGORIES (COURSE_ID, CATEGORIES_ID) VALUES (?,?)";
		fields = new Object[2];
		i = 0;
		fields[i++] = siteId;
		fields[i++] = id;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("insertCategoryTx: db write failed: " + fields[0] + " " + sql);
		}
	}

	/**
	 * Insert a new forum.
	 * 
	 * @param forum
	 *        The forum.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertForum(final Forum forum, final Long categoryId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertForumTx(forum, categoryId);
			}
		}, "insertForum");
	}

	/**
	 * Insert a new forum grade record.
	 * 
	 * @param forum
	 *        The forum.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertForumGrade(final String siteId, final Forum forum)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertForumGradeTx(siteId, forum);
			}
		}, "insertForumGrade");
	}

	/**
	 * Insert a new forum grade record (transaction code).
	 * 
	 * @param siteId
	 *        The site id.
	 * @param forum
	 *        The forum.
	 */
	protected void insertForumGradeTx(String siteId, Forum forum)
	{
		String sql = "INSERT INTO JFORUM_GRADE (CONTEXT, GRADE_TYPE, FORUM_ID, TOPIC_ID, POINTS, ADD_TO_GRADEBOOK, CATEGORIES_ID, MIN_POSTS, MIN_POSTS_REQUIRED)"
				+ " VALUES(?,?,?,?,?,?,?,?,?)";

		Object[] fields = new Object[9];
		int i = 0;
		fields[i++] = siteId;
		fields[i++] = forum.gradeType;
		fields[i++] = forum.id;
		fields[i++] = Long.valueOf(0);
		fields[i++] = forum.points;
		fields[i++] = forum.addToGradebook ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = Long.valueOf(0);
		fields[i++] = forum.minPosts;
		fields[i++] = forum.minPostsRequired ? Integer.valueOf(1) : Integer.valueOf(0);

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "grade_id");
		if (id == null)
		{
			throw new RuntimeException("insertForumGradeTx: dbInsert failed");
		}
		forum.gradeId = id;
	}

	/**
	 * Insert jforum_forum_sakai_groups_readonly_access entry.
	 * 
	 * @param forumId
	 *        The forum id.
	 * @param accessType
	 *        The access type value
	 */
	protected void insertForumGroupReadonlyAccess(final Long forumId, final Integer accessType)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertForumGroupReadonlyAccessTx(forumId, accessType);
			}
		}, "insertForumGroupReadonlyAccess");
	}

	/**
	 * Insert a jforum_forum_sakai_groups_readonly_access entry.
	 * 
	 * @param forumId
	 *        The forum id.
	 * @param accessType
	 *        The access type value
	 */
	protected void insertForumGroupReadonlyAccessTx(Long forumId, Integer accessType)
	{
		String sql = "INSERT INTO JFORUM_FORUM_SAKAI_GROUPS_READONLY_ACCESS (FORUM_ID, ACCESS_TYPE) VALUES(?,?)";

		Object[] fields = new Object[2];
		fields[0] = forumId;
		fields[1] = accessType;

		this.sqlService.dbWrite(null, sql.toLowerCase(), fields);
	}

	/**
	 * Insert forum group entries.
	 * 
	 * @param forumId
	 *        The forum id.
	 * @param groupIds
	 *        The List<String> of group ids.
	 */
	protected void insertForumGroups(final Long forumId, List<String> groupIds)
	{
		for (final String gid : groupIds)
		{
			this.sqlService.transact(new Runnable()
			{
				public void run()
				{
					insertForumGroupTx(forumId, gid);
				}
			}, "insertForumGroups");
		}
	}

	/**
	 * Insert a forum group entry.
	 * 
	 * @param forumId
	 *        The forum id.
	 * @param groupId
	 *        The group id.
	 */
	protected void insertForumGroupTx(Long forumId, String groupId)
	{
		String sql = "INSERT INTO JFORUM_FORUM_SAKAI_GROUPS (FORUM_ID, SAKAI_GROUP_ID) VALUES(?,?)";

		Object[] fields = new Object[2];
		fields[0] = forumId;
		fields[1] = groupId;

		this.sqlService.dbWrite(null, sql.toLowerCase(), fields);
	}

	/**
	 * Insert a new forum (transaction code).
	 * 
	 * @param forum
	 *        The forum.
	 * @param categoryId
	 *        The category id.
	 */
	@SuppressWarnings("unchecked")
	protected void insertForumTx(Forum forum, Long categoryId)
	{
		String sql = "SELECT MAX(FORUM_ORDER)+1 FROM JFORUM_FORUMS";
		List<String> rv = this.sqlService.dbRead(sql.toString(), null, null);
		Integer order = Integer.valueOf(1);
		if ((rv != null) && (!rv.isEmpty()))
		{
			order = Integer.valueOf(rv.get(0));
		}

		sql = "INSERT INTO JFORUM_FORUMS (CATEGORIES_ID, FORUM_NAME, FORUM_DESC, MODERATED, FORUM_TYPE, FORUM_ACCESS_TYPE, FORUM_GRADE_TYPE,"
				+ " START_DATE, END_DATE, LOCK_END_DATE, FORUM_ORDER, ALLOW_UNTIL_DATE, HIDE_UNTIL_OPEN, FORUM_TOPIC_ORDER, FORUM_TOPIC_LIKES) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";

		Object[] fields = new Object[15];
		int i = 0;
		fields[i++] = categoryId;
		fields[i++] = forum.name;
		fields[i++] = forum.description;
		fields[i++] = forum.moderated ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = forum.type;
		fields[i++] = forum.accessType;
		fields[i++] = forum.gradeType;
		Time t = null;
		if (forum.startDate != null) t = this.timeService.newTime(forum.startDate);
		fields[i++] = t;
		t = null;
		if (forum.endDate != null) t = this.timeService.newTime(forum.endDate);
		fields[i++] = t;
		fields[i++] = forum.lockOnEnd ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = order;
		t = null;
		if (forum.allowUntilDate != null) t = this.timeService.newTime(forum.allowUntilDate);
		fields[i++] = t;
		fields[i++] = forum.hideUntilOpen ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = forum.forumTopicOrder;
		fields[i++] = forum.forumTopicLikes;

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "forum_id");
		if (id == null)
		{
			throw new RuntimeException("insertForumTx: dbInsert failed");
		}

		// set the id
		forum.id = id;
	}

	/**
	 * Insert a new post (transaction code).
	 * 
	 * @param post
	 *        The post.
	 */
	protected void insertPost(final Post post, final Long topicId, final Long forumId, final Long userId, final String subject)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertPostTx(post, topicId, forumId, userId, subject);
			}
		}, "insertPost");
	}

	/**
	 * Insert a new post (transaction code).
	 * 
	 * @param post
	 *        The post.
	 */
	protected void insertPostTx(Post post, Long topicId, Long forumId, Long userId, String subject)
	{
		String sql = "INSERT INTO JFORUM_POSTS (TOPIC_ID, FORUM_ID, USER_ID, POST_TIME, ENABLE_HTML, ENABLE_SIG, POST_EDIT_TIME, ATTACH) VALUES(?,?,?,?,?,?,?,?)";

		Time now = this.timeService.newTime();
		Object[] fields = new Object[8];
		int i = 0;
		fields[i++] = topicId;
		fields[i++] = forumId;
		fields[i++] = userId;
		fields[i++] = now;
		fields[i++] = post.enableHtml ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = post.enableSig ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = now;
		fields[i++] = post.hasAttachments ? Integer.valueOf(1) : Integer.valueOf(0);

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "post_id");
		if (id == null)
		{
			throw new RuntimeException("insertPostTx: dbInsert failed");
		}

		// set the id
		post.id = id;

		// the post text
		sql = "INSERT INTO JFORUM_POSTS_TEXT (POST_ID, POST_TEXT, POST_SUBJECT) VALUES(?,?,?)";
		fields = new Object[3];
		i = 0;
		fields[i++] = id;
		fields[i++] = (post.text == null) ? this.sqlService.newEmptyString() : post.text;
		fields[i++] = subject;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("insertPostTx: dbInsert failed");
		}

		// set the topic's post ids
		sql = "UPDATE JFORUM_TOPICS SET TOPIC_FIRST_POST_ID = ?, TOPIC_LAST_POST_ID = ? WHERE TOPIC_ID = ?";
		i = 0;
		fields[i++] = id;
		fields[i++] = id;
		fields[i++] = topicId;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("insertPostTx: dbInsert failed");
		}

		// set the forum's last post to this post
		sql = "UPDATE JFORUM_FORUMS SET FORUM_LAST_POST_ID = ? WHERE FORUM_ID = ?";
		fields = new Object[2];
		i = 0;
		fields[i++] = id;
		fields[i++] = forumId;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("insertPostTx: dbInsert failed");
		}
	}

	/**
	 * Insert a new topic (transaction code).
	 * 
	 * @param topic
	 *        The topic.
	 */
	protected void insertTopic(final Topic topic)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertTopicTx(topic);
			}
		}, "insertTopic");
	}

	/**
	 * Insert a new topic grade record.
	 * 
	 * @param topic
	 *        The topic.
	 * @param siteId
	 *        The site id.
	 */
	protected void insertTopicGrade(final String siteId, final Topic topic)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				insertTopicGradeTx(siteId, topic);
			}
		}, "insertForumGrade");
	}

	/**
	 * Insert a new topic grade record (transaction code).
	 * 
	 * @param siteId
	 *        The site id.
	 * @param topic
	 *        The topic.
	 */
	protected void insertTopicGradeTx(String siteId, Topic topic)
	{
		String sql = "INSERT INTO JFORUM_GRADE (CONTEXT, GRADE_TYPE, FORUM_ID, TOPIC_ID, POINTS, ADD_TO_GRADEBOOK, CATEGORIES_ID, MIN_POSTS, MIN_POSTS_REQUIRED)"
				+ " VALUES(?,?,?,?,?,?,?,?,?)";

		Object[] fields = new Object[9];
		int i = 0;
		fields[i++] = siteId;
		fields[i++] = Long.valueOf(1); // grade by topic
		fields[i++] = topic.forumId;
		fields[i++] = topic.id;
		fields[i++] = topic.points;
		fields[i++] = topic.addToGradebook ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = Long.valueOf(0);
		fields[i++] = topic.minPosts;
		fields[i++] = topic.minPostsRequired ? Integer.valueOf(1) : Integer.valueOf(0);

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "grade_id");
		if (id == null)
		{
			throw new RuntimeException("insertTopicGradeTx: dbInsert failed");
		}
		topic.gradeId = id;
	}

	/**
	 * Insert a new topic (transaction code).
	 * 
	 * @param topic
	 *        The topic.
	 */
	protected void insertTopicTx(Topic topic)
	{
		String sql = "INSERT INTO JFORUM_TOPICS (FORUM_ID, TOPIC_TITLE, USER_ID, TOPIC_TIME, TOPIC_STATUS, TOPIC_TYPE, MODERATED,"
				+ " TOPIC_GRADE, TOPIC_EXPORT, START_DATE, END_DATE, LOCK_END_DATE, ALLOW_UNTIL_DATE, HIDE_UNTIL_OPEN) VALUES(?,?,?,?,?,?,?,?,1,?,?,?,?,?)";

		Object[] fields = new Object[13];
		int i = 0;
		fields[i++] = topic.forumId;
		fields[i++] = topic.title;
		fields[i++] = topic.userId;
		fields[i++] = this.timeService.newTime();
		fields[i++] = topic.status;
		fields[i++] = topic.type;
		fields[i++] = topic.moderated ? Integer.valueOf(1) : Integer.valueOf(0);
		fields[i++] = topic.grade ? Integer.valueOf(1) : Integer.valueOf(0);
		Time t = null;
		if (topic.startDate != null) t = this.timeService.newTime(topic.startDate);
		fields[i++] = t;
		t = null;
		if (topic.endDate != null) t = this.timeService.newTime(topic.endDate);
		fields[i++] = t;
		fields[i++] = topic.lockOnEnd ? Integer.valueOf(1) : Integer.valueOf(0);
		t = null;
		if (topic.allowUntilDate != null) t = this.timeService.newTime(topic.allowUntilDate);
		fields[i++] = t;
		fields[i++] = topic.hideUntilOpen ? Integer.valueOf(1) : Integer.valueOf(0);

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "topic_id");
		if (id == null)
		{
			throw new RuntimeException("insertTopicTx: dbInsert failed");
		}

		// set the id
		topic.id = id;

		// set the forum topic count to one greater
		sql = "UPDATE JFORUM_FORUMS SET FORUM_TOPICS = FORUM_TOPICS+1 WHERE FORUM_ID = ?";
		fields = new Object[1];
		fields[0] = topic.forumId;
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("insertTopicTx: dbInsert failed");
		}
	}

	/**
	 * Insert a new JForum user.
	 * 
	 * @param userId
	 *        The sakai user id.
	 */
	protected Long insertUser(final User user)
	{
		final Id id = new Id();
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				id.id = insertUserTx(user);
			}
		}, "insertUser");

		return id.id;
	}

	/**
	 * Insert a new JForum user (transaction code).
	 * 
	 * @param userId
	 *        The sakai user id.
	 */
	protected Long insertUserTx(User user)
	{
		String sql = "INSERT INTO JFORUM_USERS (SAKAI_USER_ID, USERNAME, USER_PASSWORD, USER_REGDATE, USER_LNAME, USER_FNAME) VALUES(?,?,?,?,?,?)";

		Object[] fields = new Object[6];
		int i = 0;
		fields[i++] = user.getId();
		fields[i++] = user.getEid();
		fields[i++] = "sso";
		fields[i++] = this.timeService.newTime();
		fields[i++] = user.getLastName();
		fields[i++] = user.getFirstName();

		Long id = this.sqlService.dbInsert(null, sql.toLowerCase(), fields, "user_id");
		if (id == null)
		{
			throw new RuntimeException("insertUserTx: dbInsert failed");
		}

		// return the id
		return id;
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
		String sql = "SELECT C.CATEGORIES_ID, C.TITLE, C.DISPLAY_ORDER, C.MODERATED                                               "
				+ " FROM JFORUM_CATEGORIES C                                                                                      "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_CATEGORIES CC ON C.CATEGORIES_ID = CC.CATEGORIES_ID                            "
				+ " WHERE CC.COURSE_ID = ? AND C.ARCHIVED = 0 ORDER BY C.DISPLAY_ORDER ASC";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Category> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Category c = new Category();
					c.id = Long.valueOf(StringUtil.trimToNull(result.getString(1)));
					c.title = StringUtil.trimToNull(result.getString(2));
					c.displayOrder = Long.valueOf(StringUtil.trimToNull(result.getString(3)));
					c.moderated = "1".equals(StringUtil.trimToNull(result.getString(4)));

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
	 *        The category Id
	 * @return List<Forum> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Forum> readForums(final Long categoryId)
	{
		String sql = "SELECT F.FORUM_ID, F.FORUM_NAME, F.FORUM_DESC, F.FORUM_ORDER, F.MODERATED, F.START_DATE, F.END_DATE,"
				+ " F.FORUM_TYPE, F.FORUM_ACCESS_TYPE, F.FORUM_GRADE_TYPE                                                 "
				+ " FROM JFORUM_FORUMS F WHERE F.CATEGORIES_ID = ? ORDER BY F.FORUM_ORDER ASC";
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
					f.id = Long.valueOf(StringUtil.trimToNull(result.getString(1)));
					f.name = StringUtil.trimToNull(result.getString(2));
					f.description = StringUtil.trimToNull(result.getString(3));
					f.seq = Long.valueOf(StringUtil.trimToNull(result.getString(4)));
					f.moderated = "1".equals(StringUtil.trimToNull(result.getString(5)));
					Timestamp ts = result.getTimestamp(6, ss.getCal());
					f.startDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					ts = result.getTimestamp(7, ss.getCal());
					f.endDate = (ts == null) ? null : Long.valueOf(ts.getTime());
					f.type = Long.valueOf(StringUtil.trimToNull(result.getString(8)));
					f.accessType = Long.valueOf(StringUtil.trimToNull(result.getString(9)));
					f.gradeType = Long.valueOf(StringUtil.trimToNull(result.getString(10)));

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
	 * Read topics for this forum - title only.
	 * 
	 * @param forumId
	 *        The forum Id
	 * @return List<Topic> for the forums found in the category.
	 */
	@SuppressWarnings("unchecked")
	protected List<Topic> readTopics(final Long forumId)
	{
		String sql = "SELECT T.TOPIC_ID, T.TOPIC_TITLE FROM JFORUM_TOPICS T WHERE T.FORUM_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = forumId;

		List<Topic> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					Topic t = new Topic();
					t.id = Long.valueOf(StringUtil.trimToNull(result.getString(1)));
					t.title = StringUtil.trimToNull(result.getString(2));

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
	 * Insert a new category.
	 * 
	 * @param category
	 *        The category.
	 * @param siteId
	 *        The site id.
	 */
	protected void setInited(final String siteId)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				setInitedTx(siteId);
			}
		}, "setInited");
	}

	/**
	 * Insert a new category (transaction code).
	 * 
	 * @param category
	 *        The category.
	 * @param siteId
	 *        The site id.
	 */
	@SuppressWarnings("unchecked")
	protected void setInitedTx(String siteId)
	{
		// if the record is there, we are done
		String sql = "SELECT COUNT(1) FROM JFORUM_SAKAI_COURSE_INITVALUES WHERE COURSE_ID = ?";
		Object[] fields = new Object[1];
		fields[0] = siteId;

		List<Integer> rv = this.sqlService.dbRead(sql.toString().toLowerCase(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					int count = result.getInt(1);
					return Integer.valueOf(count);
				}
				catch (SQLException e)
				{
					M_log.warn("setInitedTx_check: " + e.toString());
					return null;
				}
				catch (IndexOutOfBoundsException e)
				{
					return null;
				}
			}
		});
		if ((!rv.isEmpty()) && (rv.get(0).intValue() > 0)) return;

		// otherwise insert
		sql = "INSERT INTO JFORUM_SAKAI_COURSE_INITVALUES (COURSE_ID, INIT_STATUS) VALUES (?,1)";
		if (!this.sqlService.dbWrite(sql.toLowerCase(), fields))
		{
			throw new RuntimeException("setInitedTx: db write failed: " + fields[0] + " " + sql);
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
	 * Write a file for this attachment.
	 * 
	 * @param attachment
	 *        The attachment info.
	 * @param body
	 *        The body bytes.
	 */
	protected void writeFile(Attachment attachment, byte[] body)
	{
		if (this.fileRoot == null) return;

		String extension = "";
		int pos = attachment.name.lastIndexOf('.');
		if (pos != -1)
		{
			extension = attachment.name.substring(pos);
		}

		// create a new file name Note: the date in the path is slightly different from how JForum does it, and is just like CHS does it.
		String relativePath = this.timeService.newTime().toStringFilePath();
		String relativeName = relativePath + IdManager.createUuid() + extension;

		attachment.path = relativeName;

		FileOutputStream out = null;
		try
		{
			// assure that the path is created
			File file = new File(this.fileRoot + relativePath);
			file.mkdirs();

			// write the file
			file = new File(this.fileRoot + relativeName);
			out = new FileOutputStream(file);
			out.write(body);
		}
		catch (IOException e)
		{
			M_log.warn("writeFile: " + e);
		}
		finally
		{
			try
			{
				if (out != null)
				{
					out.close();
				}
			}
			catch (IOException e)
			{
				M_log.warn("writeFile: close: " + e);
			}
		}
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
		if (!this.sqlService.dbWrite(query.toLowerCase(), fields))
		{
			throw new RuntimeException("writeTx: db write failed: " + fields[0] + " " + query);
		}
	}
}
