/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/PurgeJforumHandler.java $
 * $Id: PurgeJforumHandler.java 8256 2014-06-13 20:11:14Z ggolden $
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
import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.PurgeHandler;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.db.api.SqlService;

/**
 * Archives PurgeHandler for JForum. Note: JForum's table names are created in lower case
 */
public class PurgeJforumHandler implements PurgeHandler
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(PurgeJforumHandler.class);

	/** The application Id. */
	protected final static String applicationId = "sakai.jforum.tool";

	/** Dependency: ArchiveService. */
	protected ArchivesService archivesService = null;

	/** Configuration: The file system root to the attachment files. */
	protected String fileRoot = null;

	/** Dependency: ServerConfigurationService */
	protected ServerConfigurationService serverConfigurationService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/**
	 * Shutdown.
	 */
	public void destroy()
	{
		this.archivesService.unRegisterPurgeHandler(applicationId, this);
		M_log.info("destroy()");
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		this.archivesService.registerPurgeHandler(applicationId, this);

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
	 * {@inheritDoc}
	 */
	public void purge(String siteId)
	{
		M_log.info("purge " + applicationId + " in site: " + siteId);

		// search
		purgeSearch(siteId);

		// private messages
		purgePrivateMessages(siteId);

		// forums
		purgeForums(siteId);

		// evaluation
		purgeEvaluation(siteId);

		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete("DELETE FROM JFORUM_SITE_USERS WHERE SAKAI_SITE_ID = ?".toLowerCase(), fields);
		delete("DELETE FROM JFORUM_SAKAI_SESSIONS WHERE COURSE_ID = ?".toLowerCase(), fields);
		delete("DELETE FROM JFORUM_SAKAI_COURSE_INITVALUES WHERE COURSE_ID = ?".toLowerCase(), fields);
		delete("DELETE FROM JFORUM_IMPORT WHERE SAKAI_SITE_ID = ?".toLowerCase(), fields);
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
	 * {@inheritDoc}
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Do a delete.
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void delete(final String query, final Object[] fields)
	{
		this.sqlService.transact(new Runnable()
		{
			public void run()
			{
				deleteTx(query, fields);
			}
		}, "delete: " + fields[0] + " " + query);
	}

	/**
	 * Delete the attachment file
	 * 
	 * @param path
	 *        The file path, relative to the root.
	 */
	protected void deleteFile(String path)
	{
		if (this.fileRoot == null) return;

		try
		{
			// the external file
			String name = this.fileRoot + path;
			File file = new File(name);

			// delete
			if (file.exists())
			{
				File container = file.getParentFile();

				file.delete();

				// walk back clearing empty directories
				String root = new File(this.fileRoot).getCanonicalPath();
				while (true)
				{
					// don't delete the root path
					if (container.getCanonicalPath().equals(root)) break;

					File toDelete = container;
					container = toDelete.getParentFile();

					if (!toDelete.delete()) break;
				}
			}
		}
		catch (IOException e)
		{
			M_log.warn("deleteFile: " + e);
		}
	}

	/**
	 * Do a delete (transaction code)
	 * 
	 * @param query
	 *        The delete query.
	 * @param fields
	 *        the prepared statement fields.
	 */
	protected void deleteTx(String query, Object[] fields)
	{
		if (!this.sqlService.dbWrite(query, fields))
		{
			throw new RuntimeException("deleteTx: db write failed: " + fields[0] + " " + query);
		}
	}

	/**
	 * Purge the evaluation part of JForum
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void purgeEvaluation(String siteId)
	{
		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete(("DELETE JFORUM_EVALUATIONS FROM JFORUM_EVALUATIONS                                                                          "
				+ " INNER JOIN JFORUM_GRADE ON JFORUM_EVALUATIONS.GRADE_ID = JFORUM_GRADE.GRADE_ID                                          "
				+ " WHERE JFORUM_GRADE.CONTEXT = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_SCHEDULE_GRADES_GRADEBOOK FROM JFORUM_SCHEDULE_GRADES_GRADEBOOK                                              "
				+ " INNER JOIN JFORUM_GRADE ON JFORUM_SCHEDULE_GRADES_GRADEBOOK.GRADE_ID = JFORUM_GRADE.GRADE_ID                            "
				+ " WHERE JFORUM_GRADE.CONTEXT = ?").toLowerCase(), fields);

		delete(("DELETE FROM JFORUM_GRADE                                                                                                   "
				+ " WHERE JFORUM_GRADE.CONTEXT = ?").toLowerCase(), fields);
	}

	/**
	 * Purge the forums part of JForum
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void purgeForums(String siteId)
	{
		Object[] fields = new Object[1];
		fields[0] = siteId;

		// get and delete the attachments files
		List<String> attachmentFiles = readForumAttachments(fields);
		for (String path : attachmentFiles)
		{
			deleteFile(path);
		}

		delete(("DELETE JFORUM_ATTACH_DESC FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                              "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_ATTACH ON JFORUM_POSTS.POST_ID = JFORUM_ATTACH.POST_ID                                                "
				+ " INNER JOIN JFORUM_ATTACH_DESC ON JFORUM_ATTACH.ATTACH_ID = JFORUM_ATTACH_DESC.ATTACH_ID                                 "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_ATTACH FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                                   "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_ATTACH ON JFORUM_POSTS.POST_ID = JFORUM_ATTACH.POST_ID                                                "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_TOPICS_MARK FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                              "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_TOPICS ON JFORUM_FORUMS.FORUM_ID = JFORUM_TOPICS.FORUM_ID                                             "
				+ " INNER JOIN JFORUM_TOPICS_MARK ON JFORUM_TOPICS.TOPIC_ID = JFORUM_TOPICS_MARK.TOPIC_ID                                   "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_TOPICS_WATCH FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                             "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_TOPICS ON JFORUM_FORUMS.FORUM_ID = JFORUM_TOPICS.FORUM_ID                                             "
				+ " INNER JOIN JFORUM_TOPICS_WATCH ON JFORUM_TOPICS.TOPIC_ID = JFORUM_TOPICS_WATCH.TOPIC_ID                                 "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_SPECIAL_ACCESS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                           "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_TOPICS ON JFORUM_FORUMS.FORUM_ID = JFORUM_TOPICS.FORUM_ID                                             "
				+ " INNER JOIN JFORUM_SPECIAL_ACCESS ON JFORUM_TOPICS.TOPIC_ID = JFORUM_SPECIAL_ACCESS.TOPIC_ID                             "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_BOOKMARKS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                                "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_TOPICS ON JFORUM_FORUMS.FORUM_ID = JFORUM_TOPICS.FORUM_ID                                             "
				+ " INNER JOIN JFORUM_BOOKMARKS ON JFORUM_TOPICS.TOPIC_ID = JFORUM_BOOKMARKS.RELATION_ID                                    "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_POSTS_TEXT FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                               "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_POSTS_TEXT ON JFORUM_POSTS.POST_ID = JFORUM_POSTS_TEXT.POST_ID                                        "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_POST_USER_LIKES FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                          "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_POST_USER_LIKES ON JFORUM_POSTS.POST_ID = JFORUM_POST_USER_LIKES.POST_ID                              "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_POSTS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                                    "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_FORUMS_WATCH FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                             "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_FORUMS_WATCH ON JFORUM_FORUMS.FORUM_ID = JFORUM_FORUMS_WATCH.FORUM_ID                                 "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_FORUM_SAKAI_GROUPS_READONLY_ACCESS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                       "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_FORUM_SAKAI_GROUPS_READONLY_ACCESS ON JFORUM_FORUMS.FORUM_ID = JFORUM_FORUM_SAKAI_GROUPS_READONLY_ACCESS.FORUM_ID"
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_SPECIAL_ACCESS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                           "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_SPECIAL_ACCESS ON JFORUM_FORUMS.FORUM_ID = JFORUM_SPECIAL_ACCESS.FORUM_ID                             "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_TOPICS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                                   "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_TOPICS ON JFORUM_FORUMS.FORUM_ID = JFORUM_TOPICS.FORUM_ID                                             "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_FORUM_SAKAI_GROUPS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                       "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_FORUM_SAKAI_GROUPS ON JFORUM_FORUMS.FORUM_ID = JFORUM_FORUM_SAKAI_GROUPS.FORUM_ID                     "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_FORUMS FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                                   "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_CATEGORIES FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                               "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		delete("DELETE FROM JFORUM_SAKAI_COURSE_CATEGORIES WHERE COURSE_ID = ?".toLowerCase(), fields);
	}

	/**
	 * Purge the private messages part of JForum
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void purgePrivateMessages(String siteId)
	{
		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete(("DELETE JFORUM_PRIVMSGS_TEXT FROM JFORUM_PRIVMSGS_TEXT                                                                      "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS_TEXT.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID  "
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase(), fields);

		// get and delete the attachments files
		List<String> attachmentFiles = readPrivateMessagesAttachments(fields);
		for (String path : attachmentFiles)
		{
			deleteFile(path);
		}

		delete(("DELETE JFORUM_ATTACH_DESC FROM JFORUM_PRIVMSGS_ATTACH                                                                      "
				+ " INNER JOIN JFORUM_ATTACH_DESC ON JFORUM_PRIVMSGS_ATTACH.ATTACH_ID = JFORUM_ATTACH_DESC.ATTACH_ID                        "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS_ATTACH.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID"
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_ATTACH FROM JFORUM_PRIVMSGS_ATTACH                                                                           "
				+ " INNER JOIN JFORUM_ATTACH ON JFORUM_PRIVMSGS_ATTACH.ATTACH_ID = JFORUM_ATTACH.ATTACH_ID                                  "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS_ATTACH.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID"
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_PRIVMSGS_ATTACH FROM JFORUM_PRIVMSGS_ATTACH                                                                  "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS_ATTACH.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID"
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase(), fields);

		delete(("DELETE JFORUM_PRIVMSGS FROM JFORUM_PRIVMSGS                                                                                "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID       "
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase(), fields);

		delete("DELETE FROM JFORUM_SAKAI_COURSE_PRIVMSGS WHERE COURSE_ID = ?".toLowerCase(), fields);
	}

	protected void purgeSearch(String siteId)
	{
		Object[] fields = new Object[1];
		fields[0] = siteId;

		delete(("DELETE JFORUM_SEARCH_WORDMATCH FROM JFORUM_SAKAI_COURSE_CATEGORIES                                                         "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_SEARCH_WORDMATCH ON JFORUM_POSTS.POST_ID = JFORUM_SEARCH_WORDMATCH.POST_ID                            "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase(), fields);

		// TODO: find a way to remove any JFORUM_SEARCH_WORD that has a WORD_ID not in JFORUM_SEARCH_WORDMATCH
		// DELETE FROM JFORUM_SEARCH_WORDS WHERE WORD_ID NOT IN (SELECT WORD_ID FROM JFORUM_SEARCH_WORDMATCH)
	}

	/**
	 * Read the file names (relative to the JForum file root) for the attachments to the forums
	 * 
	 * @param fields
	 *        The site id packed into an object array.
	 * @return The List<String> of attachment file names, possibly empty.
	 */
	@SuppressWarnings("unchecked")
	protected List<String> readForumAttachments(Object[] fields)
	{
		String sql = ("SELECT JFORUM_ATTACH_DESC.PHYSICAL_FILENAME FROM JFORUM_SAKAI_COURSE_CATEGORIES                                      "
				+ " INNER JOIN JFORUM_CATEGORIES ON JFORUM_SAKAI_COURSE_CATEGORIES.CATEGORIES_ID = JFORUM_CATEGORIES.CATEGORIES_ID          "
				+ " INNER JOIN JFORUM_FORUMS ON JFORUM_CATEGORIES.CATEGORIES_ID = JFORUM_FORUMS.CATEGORIES_ID                               "
				+ " INNER JOIN JFORUM_POSTS ON JFORUM_FORUMS.FORUM_ID = JFORUM_POSTS.FORUM_ID                                               "
				+ " INNER JOIN JFORUM_ATTACH ON JFORUM_POSTS.POST_ID = JFORUM_ATTACH.POST_ID                                                "
				+ " INNER JOIN JFORUM_ATTACH_DESC ON JFORUM_ATTACH.ATTACH_ID = JFORUM_ATTACH_DESC.ATTACH_ID                                 "
				+ " WHERE JFORUM_SAKAI_COURSE_CATEGORIES.COURSE_ID = ?").toLowerCase();

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);
		return rv;
	}

	/**
	 * Read the file names (relative to the JForum file root) for the attachments to the private message
	 * 
	 * @param fields
	 *        The site id packed into an object array.
	 * @return The List<String> of attachment file names, possibly empty.
	 */
	@SuppressWarnings("unchecked")
	protected List<String> readPrivateMessagesAttachments(Object[] fields)
	{
		String sql = ("SELECT JFORUM_ATTACH_DESC.PHYSICAL_FILENAME FROM JFORUM_PRIVMSGS_ATTACH                                              "
				+ " INNER JOIN JFORUM_ATTACH_DESC ON JFORUM_PRIVMSGS_ATTACH.ATTACH_ID = JFORUM_ATTACH_DESC.ATTACH_ID                        "
				+ " INNER JOIN JFORUM_SAKAI_COURSE_PRIVMSGS ON JFORUM_PRIVMSGS_ATTACH.PRIVMSGS_ID = JFORUM_SAKAI_COURSE_PRIVMSGS.PRIVMSGS_ID"
				+ " WHERE JFORUM_SAKAI_COURSE_PRIVMSGS.COURSE_ID = ?").toLowerCase();

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, null);
		return rv;
	}
}
