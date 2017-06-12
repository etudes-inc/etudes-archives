/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-impl/impl/src/java/org/etudes/archives/impl/ArchivesServiceImpl.java $
 * $Id: ArchivesServiceImpl.java 11057 2015-06-05 16:56:06Z rashmim $
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

package org.etudes.archives.impl;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.ArchiveDescription;
import org.etudes.archives.api.ArchiveHandler;
import org.etudes.archives.api.Archiver;
import org.etudes.archives.api.ArchivesRecorder;
import org.etudes.archives.api.ArchivesService;
import org.etudes.archives.api.Artifact;
import org.etudes.archives.api.ImportHandler;
import org.etudes.archives.api.PurgeAttachmentHandler;
import org.etudes.archives.api.PurgeHandler;
import org.etudes.archives.api.PurgeUserHandler;
import org.etudes.archives.api.Subset;
import org.etudes.archives.api.TermHandler;
import org.etudes.archives.api.XrefHandler;
import org.etudes.homepage.api.HomePageService;
import org.etudes.util.XrefHelper;
import org.sakaiproject.authz.api.SecurityAdvisor;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.cover.ComponentManager;
import org.sakaiproject.db.api.SqlReader;
import org.sakaiproject.db.api.SqlService;
import org.sakaiproject.exception.PermissionException;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.thread_local.api.ThreadLocalManager;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.StringUtil;
import org.sakaiproject.util.Validator;

/**
 * ArchivesServiceImpl implements ArchivesService.
 */
public class ArchivesServiceImpl implements ArchivesService
{
	/** MS to sleep between multi-item tasks, after each item. */
	protected static final int SLEEPY_TIME = 1000;

	/** MS to sleep between multi-item tasks, after each 100 items. */
	protected static final int SLEEPY_TIME_LONG = 60000;

	/** Our logger. */
	private static Log M_log = LogFactory.getLog(ArchivesServiceImpl.class);

	/** Our archive handlers - keyed by application id. */
	protected Map<String, ArchiveHandler> archiveHandlers = new HashMap<String, ArchiveHandler>();

	/** Our archiver. */
	protected Archiver archiver = null;

	/** Our import handlers - keyed by application id. */
	protected Map<String, ImportHandler> importHandlers = new HashMap<String, ImportHandler>();

	/** Our purge attachment handler. */
	protected PurgeAttachmentHandler purgeAttachmentHandler = null;

	/** Our purge handlers - keyed by application id. */
	protected Map<String, PurgeHandler> purgeHandlers = new HashMap<String, PurgeHandler>();

	/** Our purge user handlers - keyed by application id. */
	protected Map<String, PurgeUserHandler> purgeUserHandlers = new HashMap<String, PurgeUserHandler>();

	/** Our recorder. */
	protected ArchivesRecorder recorder = null;

	/** Dependency: SecurityService */
	protected SecurityService securityService = null;

	/** Dependency: SessionManager */
	protected SessionManager sessionManager = null;

	/** Dependency: SiteService */
	protected SiteService siteService = null;

	/** Dependency: SqlService. */
	protected SqlService sqlService = null;

	/** The task thread. */
	protected Thread task = null;

	/** The task status. */
	protected StringBuilder taskStatusBuf = new StringBuilder(1024);

	/** Semaphore for task data. */
	protected Object taskSync = new Object();

	/** Our term handler. */
	protected TermHandler termHandler = null;

	/** Dependency: SessionManager */
	protected ThreadLocalManager threadLocalManager = null;

	/** Dependency: UserDirectoryService */
	protected UserDirectoryService userDirectoryService = null;

	/** Our xref handlers - keyed by application id. */
	protected Map<String, XrefHandler> xrefHandlers = new HashMap<String, XrefHandler>();

	/**
	 * {@inheritDoc}
	 */
	public void archiveSite(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.message("archiveSite: please specify a valid site id");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}
			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("archiveSite: ", siteId);

						// create an archive for the site
						Archive archive = impl.archiver.newArchive(siteId);
			
						// ask each of our archive handlers to add artifacts (skip CHS for now)
						for (ArchiveHandler handler : archiveHandlers.values())
						{
							// skip the lasts for now
							if (!handler.getApplicationId().equals("sakai.resources") && !handler.getApplicationId().equals("e3.gradebook"))
							{
								try
								{
									handler.archive(siteId, archive);
								}
								catch (Throwable t)
								{
									impl.message("archiveSite_task: failure in handler: ", handler.toString(), ": ", t.toString());
								}
							}
						}

						// run CHS lasts
						for (ArchiveHandler handler : archiveHandlers.values())
						{
							if (handler.getApplicationId().equals("sakai.resources") || handler.getApplicationId().equals("e3.gradebook"))
							{
								try
								{
									handler.archive(siteId, archive);
								}
								catch (Throwable t)
								{
									synchronized (impl.taskSync)
									{
										impl.message("archiveSite_task: failure in handler: ", handler.toString(), ": ", t.toString());
									}
								}
							}
						}

						// finish up the archive
						archive.complete();

						// record site as archived
						if (impl.recorder != null)
						{
							impl.recorder.recordArchive(siteId);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void archiveTerm(final String termId, final Subset page, final String institutionCode) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((termId == null) || (termId.length() == 0))
		{
			this.messageNoLog("archiveTerm: please specify a valid term id");
			return;
		}

		if ((page == null) || (page.getSize() == null) || (page.getSize() <= 0) || (page.getPage() == null) || (page.getPage() < 0))
		{
			this.messageNoLog("archiveTerm: please specify a valid page");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						String institutionMessage = null;
						if ((institutionCode == null) || (institutionCode.length() == 0))
						{
							institutionMessage = "";
						}
						else
						{
							institutionMessage = " [" + institutionCode + "]";
						}

						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("archiveTerm: ", termId + " page: " + page.getPage() + " (" + page.getSize() + ")", institutionMessage);

						// get the sites for the term
						List<String> siteIds = getTermSiteIds(termId, page, institutionCode);
						if (siteIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("archiveTerm: ", termId, " page: " + page.getPage(), institutionMessage, " : has no sites");
							}

							return;
						}

						int siteNum = 0;
						int numSites = siteIds.size();
						for (String siteId : siteIds)
						{
							siteNum++;
							impl.message("archiveTerm: term: ", termId, institutionMessage, " site ", siteNum, " of ", numSites, " : ", siteId);

							// create an archive for the site
							Archive archive = impl.archiver.newArchive(siteId);

							// ask each of our archive handlers to add artifacts - skip CHS for now
							for (ArchiveHandler handler : archiveHandlers.values())
							{
								// skip the lasts for now
								if (!handler.getApplicationId().equals("sakai.resources"))
								{
									try
									{
										handler.archive(siteId, archive);
									}
									catch (Throwable t)
									{
										impl.message("archiveTerm_task: failure in handler: ", handler.toString(), ": ", t.toString());
									}
								}
							}

							// run CHS last
							for (ArchiveHandler handler : archiveHandlers.values())
							{
								if (handler.getApplicationId().equals("sakai.resources"))
								{
									try
									{
										handler.archive(siteId, archive);
									}
									catch (Throwable t)
									{
										impl.message("archiveTerm_task: failure in handler: ", handler.toString(), ": ", t.toString());
									}
								}
							}

							// finish up the archive
							archive.complete();

							// record site as archived
							if (impl.recorder != null)
							{
								impl.recorder.recordArchive(siteId);
							}

							// clear the thread-local to remove any caching that XrefHelper did in the context of the site we just processed
							XrefHelper.clearThreadCaches();

							sleep(siteNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void deleteSiteArchive(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.messageNoLog("deleteSiteArchive: please specify a valid site id");

			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("deleteSiteArchive: ", siteId);

						// remove the archive file
						impl.archiver.deleteArchive(siteId);

						// remove the archive records
						if (impl.recorder != null)
						{
							impl.recorder.recordArchiveDelete(siteId);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void deleteTermArchive(final String termId, final Subset page, final String institutionCode) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((termId == null) || (termId.length() == 0))
		{
			this.messageNoLog("deleteTermArchive: please specify a valid term id");

			return;
		}

		if ((page == null) || (page.getSize() == null) || (page.getSize() <= 0) || (page.getPage() == null) || (page.getPage() < 0))
		{
			this.messageNoLog("deleteTermArchive: please specify a valid page");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						String institutionMessage = null;
						if ((institutionCode == null) || (institutionCode.length() == 0))
						{
							institutionMessage = "";
						}
						else
						{
							institutionMessage = " [" + institutionCode + "]";
						}

						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("deleteTermArchive: ", termId + " page: " + page.getPage() + " (" + page.getSize() + ")", institutionMessage);

						// get the archived site ids for the term
						List<String> siteIds = getArchivedTermSiteIds(termId, page, institutionCode);
						if (siteIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("deleteTermArchive: ", termId, " page: " + page.getPage(), institutionMessage, " has no sites");
							}

							return;
						}

						int siteNum = 0;
						int numSites = siteIds.size();
						for (String siteId : siteIds)
						{
							siteNum++;

							impl.message("deleteTermArchive: term: ", termId, institutionMessage, " site ", siteNum, " of ", numSites, " : ", siteId);

							// remove the archive file
							impl.archiver.deleteArchive(siteId);

							// remove the archive records
							if (impl.recorder != null)
							{
								impl.recorder.recordArchiveDelete(siteId);
							}

							sleep(siteNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * Returns to uninitialized state.
	 */
	public void destroy()
	{
		M_log.info("destroy()");
	}

	/**
	 * {@inheritDoc}
	 */
	public PurgeAttachmentHandler getPurgeAttachmentHandler()
	{
		return this.purgeAttachmentHandler;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getTaskStatus()
	{
		synchronized (this.taskSync)
		{
			return this.taskStatusBuf.toString();
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public TermHandler getTermHandler()
	{
		return this.termHandler;
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<String> getToolIds(String archiveSiteId)
	{
		Set<String> rv = new HashSet<String>();
		if (archiveSiteId == null) return rv;

		// TODO: replace this stub code with something more selective
		// for (String appId : this.importHandlers.keySet())
		// {
		// if (appId.equals("etudes.archives")) continue;
		// rv.add(appId);
		// }

		// read the archive artifacts, and collect which tools they represent
		Archive archive = this.archiver.readArchive(archiveSiteId);
		List<Artifact> artifacts = archive.getArtifacts();
		for (Artifact artifact : artifacts)
		{
			if (artifact.getType().equals("etudes.archives")) continue;
			rv.add(artifact.getType());
		}

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public List<ArchiveDescription> getUserArchives(String userId)
	{
		if (this.recorder == null) return new ArrayList<ArchiveDescription>();

		return this.recorder.getUserArchives(userId);
	}

	/**
	 * {@inheritDoc}
	 */
	public void importSite(final String archiveSiteId, final String siteId, final String asUserId) throws PermissionException
	{
		if ((siteId == null) || (siteId.length() == 0) || (archiveSiteId == null) || (archiveSiteId.length() == 0))
		{
			this.messageNoLog("importSite: please specify valid archive and site id");

			return;
		}

		// TODO: check that the siteId is valid?

		// for the thread
		final ArchivesServiceImpl impl = this;

		// use the current user, unless asUserId is set and the current user is a super user
		String usId = this.sessionManager.getCurrentSessionUserId();
		if (asUserId != null)
		{
			if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

			usId = asUserId;
		}
		final String userId = usId;

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}
			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("importSite: archive: ", archiveSiteId, " to site: ", siteId);

						doSiteImport(archiveSiteId, siteId, null, true);

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void importSiteImmediate(final String archiveSiteId, final String siteId, final Set<String> importToolIds) throws PermissionException
	{
		if ((siteId == null) || (siteId.length() == 0) || (archiveSiteId == null) || (archiveSiteId.length() == 0))
		{
			return;
		}

		pushAdvisor();
		try
		{
			doSiteImport(archiveSiteId, siteId, importToolIds, false);
		}
		finally
		{
			popAdvisor();
		}
	}

	/**
	 * Final initialization, once all dependencies are set.
	 */
	public void init()
	{
		try
		{
			M_log.info("init()");
		}
		catch (Throwable t)
		{
			M_log.warn("init(): ", t);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean isTaskRunning()
	{
		synchronized (this.taskSync)
		{
			return this.task != null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void listArchive(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.messageNoLog("listArchive: please specify a valid site id");

			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}
			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("listArchive: ", siteId);
						impl.messageAppendNoLog("<br />");

						// read the manifest
						Archive archive = impl.archiver.readArchive(siteId);
						List<Artifact> artifacts = archive.getArtifacts();
						for (Artifact artifact : artifacts)
						{
							impl.messageAppendNoLog(artifact.getType(), " ", artifact.getReference(), " ", artifact.getFileName(), "<br />");
							if (!artifact.getReferences().isEmpty())
							{
								for (String ref : artifact.getReferences())
								{
									impl.messageAppendNoLog("&nbsp;-&gt;&nbsp;", ref, "<br />");
								}
							}

							displayMap(1, artifact.getProperties());
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}

				@SuppressWarnings("unchecked")
				protected void displayMap(int indent, Map<String, Object> map)
				{
					if (indent > 1)
					{
						for (int i = 0; i < indent; i++)
						{
							impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
						}
						impl.messageAppendNoLog("[<br />");
						indent = indent + 1;
					}
					for (Map.Entry<String, Object> entry : map.entrySet())
					{
						// indent
						for (int i = 0; i < indent; i++)
						{
							impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
						}

						// key
						impl.messageAppendNoLog(entry.getKey(), " = ");

						// value
						if ((entry.getValue() instanceof String) || (entry.getValue() instanceof Integer) || (entry.getValue() instanceof Long)
								|| (entry.getValue() instanceof Boolean) || (entry.getValue() instanceof Float)
								|| (entry.getValue() instanceof Double))
						{
							impl.messageAppendNoLog(Validator.escapeHtml(entry.getValue().toString()), "<br />");
						}

						else if (entry.getValue() instanceof String[])
						{
							impl.messageAppendNoLog("[<br />");
							for (String str : (String[]) entry.getValue())
							{
								// indent one more
								for (int i = 0; i < indent + 1; i++)
								{
									impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
								}
								impl.messageAppendNoLog(Validator.escapeHtml(str), "<br />");
							}
							// indent one more
							for (int i = 0; i < indent + 1; i++)
							{
								impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
							}
							impl.messageAppendNoLog("]<br />");
						}

						else if (entry.getValue() instanceof Collection)
						{
							impl.messageAppendNoLog("[<br />");

							for (Object o : (Collection<Object>) entry.getValue())
							{
								if (o instanceof String)
								{
									// indent one more
									for (int i = 0; i < indent + 1; i++)
									{
										impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
									}
									impl.messageAppendNoLog(Validator.escapeHtml((String) o), "<br />");
								}

								else if (o instanceof Map)
								{
									displayMap(indent + 1, (Map<String, Object>) o);
								}
							}
							// indent one more
							for (int i = 0; i < indent + 1; i++)
							{
								impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
							}
							impl.messageAppendNoLog("]<br />");
						}

						else if (entry.getValue() instanceof Set)
						{
							impl.messageAppendNoLog("[<br />");

							for (String str : (Set<String>) entry.getValue())
							{
								// indent one more
								for (int i = 0; i < indent + 1; i++)
								{
									impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
								}
								impl.messageAppendNoLog(Validator.escapeHtml(str), "<br />");
							}
							// indent one more
							for (int i = 0; i < indent + 1; i++)
							{
								impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
							}
							impl.messageAppendNoLog("]<br />");
						}

						else if (entry.getValue() instanceof Map)
						{
							displayMap(indent + 1, (Map<String, Object>) entry.getValue());
						}
					}
					if (indent > 1)
					{
						for (int i = 0; i < indent - 1; i++)
						{
							impl.messageAppendNoLog("&nbsp;&nbsp;&nbsp;");
						}
						impl.messageAppendNoLog("]<br />");
					}

				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void listInactiveUsers(final Integer limit) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String curUserId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(curUserId);
						}

						pushAdvisor();

						impl.message("reportInactiveUsers: limit: ", limit, "<br />");

						// get the inactive users possibly limited
						List<String> userIds = getInactiveUserIds(limit);
						if (userIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("reportInactiveUsers limit: ", limit, ": no users");
							}

							return;
						}

						int userNum = 0;
						int numUsers = userIds.size();
						for (String userId : userIds)
						{
							userNum++;

							impl.messageAppendNoLog(userNum, " of ", numUsers, " : id: ", userId);

							try
							{
								User user = impl.userDirectoryService.getUser(userId);
								impl.messageAppendNoLog("  name: ", user.getSortName(), "  iid: ", user.getIidDisplay(), "  email: ",
										user.getEmail(), "<br />");
							}
							catch (UserNotDefinedException e)
							{
								impl.messageAppendNoLog("  not found", "<br />");
							}

							// clear the thread-local to remove any caching just done
							XrefHelper.clearThreadCaches();

							sleep(userNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeInactiveUsers(final Integer limit) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String curUserId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(curUserId);
						}

						pushAdvisor();

						impl.message("purgeInactiveUsers: limit: ", limit);

						// get the inactive users possibly limited
						List<String> userIds = getInactiveUserIds(limit);
						if (userIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("purgeInactiveUsers limit: ", limit, " has no users");
							}

							return;
						}

						int userNum = 0;
						int numUsers = userIds.size();
						for (String userId : userIds)
						{
							userNum++;

							impl.message("purgeInactiveUsers limit: ", limit, " user ", userNum, " of ", numUsers, " : ", userId);

							doPurgeUser(userId);

							// clear the thread-local to remove any caching just done
							XrefHelper.clearThreadCaches();

							sleep(userNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeSite(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.messageNoLog("purgeSite: please specify a valid site id");

			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("purgeSite: ", siteId);

						doPurgeSite(siteId);

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeSiteNow(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.messageNoLog("purgeSite: please specify a valid site id");

			return;
		}

		doPurgeSite(siteId);
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeTerm(final String termId, final Subset page, final String institutionCode) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((termId == null) || (termId.length() == 0))
		{
			this.messageNoLog("purgeTerm: please specify a valid term id");

			return;
		}

		if ((page == null) || (page.getSize() == null) || (page.getSize() <= 0) || (page.getPage() == null) || (page.getPage() < 0))
		{
			this.messageNoLog("purgeTerm: please specify a valid page");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						String institutionMessage = null;
						if ((institutionCode == null) || (institutionCode.length() == 0))
						{
							institutionMessage = "";
						}
						else
						{
							institutionMessage = " [" + institutionCode + "]";
						}

						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("purgeTerm: ", termId + " page: " + page.getPage() + " (" + page.getSize() + ")", institutionMessage);

						// get the sites for the term (all, even those already purged, so the paging remains consistent as we delete sites!)
						List<String> siteIds = getAllTermSiteIds(termId, page, institutionCode);
						if (siteIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("purgeTerm: ", termId, " page: " + page.getPage(), institutionMessage, " has no sites");
							}

							return;
						}

						int siteNum = 0;
						int numSites = siteIds.size();
						for (String siteId : siteIds)
						{
							siteNum++;

							impl.message("purgeTerm: term: ", termId, institutionMessage, " site ", siteNum, " of ", numSites, " : ", siteId);

							// record site as purged
							if (impl.recorder != null)
							{
								impl.recorder.recordPurge(siteId);
							}

							// ask each of our purge handlers to take a go at the data for this site
							for (PurgeHandler handler : purgeHandlers.values())
							{
								try
								{
									handler.purge(siteId);
								}
								catch (Throwable t)
								{
									impl.message("failure in handler: ", handler.toString(), ": ", t.toString());
								}
							}

							// clear the thread-local to remove any caching that XrefHelper did in the context of the site we just processed
							XrefHelper.clearThreadCaches();

							sleep(siteNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void purgeUser(final String userId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // TODO: user, lock, resource

		if ((userId == null) || (userId.length() == 0))
		{
			this.messageNoLog("purgeUser: please specify a valid user id");

			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String curUserId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(curUserId);
						}

						pushAdvisor();

						impl.message("purgeUser: ", userId);

						doPurgeUser(userId);

						synchronized (impl.taskSync)
						{
							impl.task = null;
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerArchiveHandler(ArchiveHandler handler)
	{
		M_log.info("register archive: " + handler.getApplicationId() + " " + handler);
		this.archiveHandlers.put(handler.getApplicationId(), handler);
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerArchiver(Archiver archiver)
	{
		M_log.info("register archiver");
		this.archiver = archiver;
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerImportHandler(String applicationId, ImportHandler handler)
	{
		M_log.info("register import: " + applicationId + " " + handler);
		this.importHandlers.put(applicationId, handler);
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerPurgeHandler(String applicationId, PurgeHandler handler)
	{
		M_log.info("register purge: " + applicationId + " " + handler);
		purgeHandlers.put(applicationId, handler);

		if (handler instanceof PurgeAttachmentHandler)
		{
			this.purgeAttachmentHandler = (PurgeAttachmentHandler) handler;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerPurgeUserHandler(String applicationId, PurgeUserHandler handler)
	{
		M_log.info("register purge user: " + applicationId + " " + handler);
		purgeUserHandlers.put(applicationId, handler);
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerRecorder(ArchivesRecorder recorder)
	{
		M_log.info("register recorder");
		this.recorder = recorder;
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerTermHandler(TermHandler handler)
	{
		M_log.info("register term handler");
		this.termHandler = handler;
	}

	/**
	 * {@inheritDoc}
	 */
	public void registerXrefHandler(String applicationId, XrefHandler handler)
	{
		M_log.info("register xref: " + applicationId + " " + handler);
		xrefHandlers.put(applicationId, handler);
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeXrefSite(final String siteId) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((siteId == null) || (siteId.length() == 0))
		{
			this.messageNoLog("removeXrefSite: please specify a valid site id");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("removeXrefSite: ", siteId);

						int totalFixed = 0;

						// ask each of our xref handlers to take a go at the data for this site
						for (XrefHandler handler : xrefHandlers.values())
						{
							try
							{
								totalFixed += handler.removeXref(siteId);
							}
							catch (Throwable t)
							{
								impl.message("removeXrefSite_task: failure in handler: ", handler.toString(), ": ", t.toString());
							}
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
							impl.message("removeXrefSite: site: ", siteId, " cross refs fixed: ", totalFixed);
						}
					}
					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * {@inheritDoc}
	 */
	public void removeXrefTerm(final String termId, final Subset page, final String institutionCode) throws PermissionException
	{
		if (!this.securityService.isSuperUser()) throw new PermissionException("", "", ""); // /TODO: user, lock, resource

		if ((termId == null) || (termId.length() == 0))
		{
			this.messageNoLog("removeXrefTerm: please specify a valid term id");
			return;
		}

		if ((page == null) || (page.getSize() == null) || (page.getSize() <= 0) || (page.getPage() == null) || (page.getPage() < 0))
		{
			this.messageNoLog("removeXrefTerm: please specify a valid page");
			return;
		}

		// for the thread
		final ArchivesServiceImpl impl = this;

		// get the current user
		final String userId = this.sessionManager.getCurrentSessionUserId();

		synchronized (this.taskSync)
		{
			if (this.task != null)
			{
				return;
			}

			this.task = new Thread(new Runnable()
			{
				public void run()
				{
					try
					{
						String institutionMessage = null;
						if ((institutionCode == null) || (institutionCode.length() == 0))
						{
							institutionMessage = "";
						}
						else
						{
							institutionMessage = " [" + institutionCode + "]";
						}

						// set the user into the thread
						Session s = impl.sessionManager.getCurrentSession();
						if (s != null)
						{
							s.setUserId(userId);
						}

						pushAdvisor();

						impl.message("removeXrefTerm: ", termId + " page: " + page.getPage() + " (" + page.getSize() + ")", institutionMessage);

						// get the sites for the term
						List<String> siteIds = getTermSiteIds(termId, page, institutionCode);
						if (siteIds.isEmpty())
						{
							synchronized (impl.taskSync)
							{
								impl.task = null;
								impl.message("removeXrefTerm: ", termId, " page: " + page.getPage(), institutionMessage, " has no sites");
							}

							return;
						}

						int totalFixed = 0;
						int siteNum = 0;
						int numSites = siteIds.size();

						for (String siteId : siteIds)
						{
							int countFixed = 0;
							siteNum++;

							impl.message("removeXrefTerm: ", termId, institutionMessage, " site ", siteNum, " of ", numSites, " : ", siteId);

							// ask each of our xref handlers to take a go at the data for this site
							for (XrefHandler handler : xrefHandlers.values())
							{
								try
								{
									countFixed += handler.removeXref(siteId);
								}
								catch (Throwable t)
								{
									impl.message("removeXrefTerm_task: failure in handler: ", handler.toString(), ": ", t.toString());
								}
							}

							impl.message("site ", siteNum, " of ", numSites, " : ", siteId, ": cross refs fixed: ", countFixed);

							totalFixed += countFixed;

							// clear the thread-local to remove any caching that XrefHelper did in the context of the site we just processed
							XrefHelper.clearThreadCaches();

							sleep(siteNum);
						}

						synchronized (impl.taskSync)
						{
							impl.task = null;
							impl.message("term: ", termId, " : cross refs fixed: ", totalFixed);
						}
					}

					finally
					{
						popAdvisor();

						if (impl.task != null)
						{
							impl.task = null;
							impl.messageAppendNoLog(" *** Failed ***");
						}

						impl.threadLocalManager.clear();
					}
				}
			}, getClass().getName());
		}

		task.start();
	}

	/**
	 * Dependency: SecurityService.
	 * 
	 * @param service
	 *        The SecurityService.
	 */
	public void setSecurityService(SecurityService service)
	{
		this.securityService = service;
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
	 * Set the SqlService.
	 * 
	 * @param service
	 *        The SqlService.
	 */
	public void setSqlService(SqlService service)
	{
		this.sqlService = service;
	}

	/**
	 * Dependency: ThreadLocalManager.
	 * 
	 * @param service
	 *        The ThreadLocalManager.
	 */
	public void setThreadLocalManager(ThreadLocalManager service)
	{
		this.threadLocalManager = service;
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
	 * {@inheritDoc}
	 */
	public void unRegisterArchiveHandler(ArchiveHandler handler)
	{
		M_log.info("unregister archive: " + handler.getApplicationId());
		Object old = this.archiveHandlers.remove(handler.getApplicationId());
		if (old != handler)
		{
			M_log.warn("unregistered some other archive handler for: " + handler.getApplicationId() + " registered: " + old + " requested: "
					+ handler);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterArchiver(Archiver archiver)
	{
		M_log.info("unregister archiver");
		if (this.archiver != archiver)
			M_log.warn("unRegisterArchiver: some other was registered: old: " + this.archiver + " requested: " + archiver);

		this.archiver = null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterImportHandler(String applicationId, ImportHandler handler)
	{
		M_log.info("unregister import: " + applicationId);
		Object old = this.importHandlers.remove(applicationId);
		if (old != handler)
		{
			M_log.warn("unregistered some other import handler for: " + applicationId + " registered: " + old + " requested: " + handler);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterPurgeHandler(String applicationId, PurgeHandler handler)
	{
		M_log.info("unregister purge: " + applicationId);
		Object old = purgeHandlers.remove(applicationId);
		if (old != handler)
		{
			M_log.warn("unregistered some other purge handler for: " + applicationId + " registered: " + old + " requested: " + handler);
		}

		if (this.purgeAttachmentHandler == handler)
		{
			this.purgeAttachmentHandler = null;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterPurgeUserHandler(String applicationId, PurgeUserHandler handler)
	{
		M_log.info("unregister purge user: " + applicationId);
		Object old = purgeUserHandlers.remove(applicationId);
		if (old != handler)
		{
			M_log.warn("unregistered some other purge user handler for: " + applicationId + " registered: " + old + " requested: " + handler);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterRecorder(ArchivesRecorder recorder)
	{
		M_log.info("unregister recorder");
		if (this.recorder != recorder)
			M_log.warn("unRegisterRecorder: some other was registered: old: " + this.recorder + " requested: " + recorder);

		this.recorder = null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterTermHandler(TermHandler handler)
	{
		M_log.info("unregister term handler");
		if (this.termHandler != handler)
			M_log.warn("unRegisterTermHandler: some other was registered: old: " + this.termHandler + " requested: " + handler);

		this.termHandler = null;
	}

	/**
	 * {@inheritDoc}
	 */
	public void unRegisterXrefHandler(String applicationId, XrefHandler handler)
	{
		M_log.info("unregister xref: " + applicationId);
		Object old = xrefHandlers.remove(applicationId);
		if (old != handler)
		{
			M_log.warn("unregistered some other xref handler for: " + applicationId + " registered: " + old + " requested: " + handler);
		}
	}

	/**
	 * Purge this site.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void doPurgeSite(String siteId)
	{
		// record site as purged
		if (this.recorder != null)
		{
			this.recorder.recordPurge(siteId);
		}

		// ask each of our purge handlers to take a go at the data for this site
		for (PurgeHandler handler : purgeHandlers.values())
		{
			try
			{
				handler.purge(siteId);
			}
			catch (Throwable t)
			{
				this.message("failure in handler: ", handler.toString(), ": ", t.toString());
			}
		}
	}

	/**
	 * Purge this user.
	 * 
	 * @param userId
	 *        The user id.
	 */
	protected void doPurgeUser(String userId)
	{
		// TODO: record user as purged ??
		// if (impl.recorder != null)
		// {
		// impl.recorder.recordPurge(siteId);
		// }

		// ask each of our purge user handlers to take a go at the data for this site
		for (PurgeUserHandler handler : purgeUserHandlers.values())
		{
			try
			{
				handler.purge(userId);
			}
			catch (Throwable t)
			{
				this.message("failure in handler: ", handler.toString(), ": ", t.toString());
			}
		}

		// purge the user's workspace
		doPurgeSite(this.siteService.getUserSiteId(userId));
	}

	/**
	 * Actually import the data from the archive into the site
	 * 
	 * @param archiveSiteId
	 *        The archive site id.
	 * @param siteId
	 *        The site id.
	 * @param importToolIds
	 *        A set of tool ids (i.e. sakai.announcements) to import - if null, take it all.
	 * @param report
	 *        if true, report using the message() method.
	 */
	protected void doSiteImport(String archiveSiteId, String siteId, Set<String> importToolIds, boolean report)
	{
		// read the manifest
		Archive archive = this.archiver.readArchive(archiveSiteId);
		List<Artifact> artifacts = archive.getArtifacts();

		// ask each artifact / handler to filter what they will be loading, and register in the archive's references those that will be needed
		for (Artifact artifact : artifacts)
		{
			ImportHandler handler = this.importHandlers.get(artifact.getType());
			if (handler != null)
			{
				try
				{
					handler.registerFilteredReferences(siteId, artifact, archive, importToolIds);
				}
				catch (Throwable t)
				{
					if (report) this.message("failure in handler: ", handler.toString(), ": ", t.toString());
				}
			}
		}

		// first, take care of the "etudes.archives" handler
		for (Artifact artifact : artifacts)
		{
			if (artifact.getType().equals("etudes.archives"))
			{
				ImportHandler handler = this.importHandlers.get(artifact.getType());
				if (handler != null)
				{
					if (report) this.message("importing: ", artifact.getType(), " ", artifact.getReference());
					try
					{
						handler.importArtifact(siteId, artifact, archive, importToolIds);
					}
					catch (Throwable t)
					{
						if (report) this.message("failure in handler: ", handler.toString(), ": ", t.toString());
					}
				}
			}
		}

		// next, CHS
		for (Artifact artifact : artifacts)
		{
			if (artifact.getType().equals("sakai.resources"))
			{
				ImportHandler handler = this.importHandlers.get(artifact.getType());
				if (handler != null)
				{
					if (report) this.message("importing: ", artifact.getType(), " ", artifact.getReference());
					try
					{
						handler.importArtifact(siteId, artifact, archive, importToolIds);
					}
					catch (Throwable t)
					{
						if (report) this.message("failure in handler: ", handler.toString(), ": ", t.toString());
					}
				}
			}
		}

		// now - everyone but archives and coursemap (yes, CHS gets run again)
		for (Artifact artifact : artifacts)
		{
			if (!artifact.getType().equals("etudes.archives") && !artifact.getType().equals("sakai.coursemap") && !artifact.getType().equals("e3.gradebook"))
			{
				ImportHandler handler = this.importHandlers.get(artifact.getType());
				if (handler != null)
				{
					if (report) this.message("importing: ", artifact.getType(), " ", artifact.getReference());
					try
					{
						handler.importArtifact(siteId, artifact, archive, importToolIds);
					}
					catch (Throwable t)
					{
						if (report) this.message("failure in handler: ", handler.toString(), ": ", t.toString());
					}
				}
				else
				{
					if (report) this.message("missing handler for type: ", artifact.getType(), " ref: ", artifact.getReference());
				}
			}
		}
		
		//e3.gradebook after jforum and mneme
		// finally, coursemap
		for (Artifact artifact : artifacts)
		{
			if (artifact.getType().equals("sakai.coursemap") || artifact.getType().equals("e3.gradebook"))
			{
				ImportHandler handler = this.importHandlers.get(artifact.getType());
				if (handler != null)
				{
					if (report) this.message("importing: ", artifact.getType(), " ", artifact.getReference());
					try
					{
						handler.importArtifact(siteId, artifact, archive, importToolIds);
					}
					catch (Throwable t)
					{
						if (report) this.message("failure in handler: ", handler.toString(), ": ", t.toString());
					}
				}
			}
		}

		// move site info URL and description to home page items, to accommodate loading archives from pre-homepage sites
		homePageService().convertFromSiteInfo(sessionManager().getCurrentSessionUserId(), siteId, true);
	}

	/**
	 * Get the site ids for the term - all sites, even if missing.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        The subset of the term to get.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null.
	 * @return The List of site ids, possibly empty, for the term.
	 */
	protected List<String> getAllTermSiteIds(String termId, Subset page, String institutionCode)
	{
		List<String> siteIds = new ArrayList<String>();
		if (this.termHandler != null) siteIds.addAll(this.termHandler.getAllTermSiteIds(termId, page, institutionCode));

		return siteIds;
	}

	/**
	 * Get the site ids of the archives for the term, possibly filtered by client prefix.
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        The subset of the term to get.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null.
	 * @return The List of site ids, possibly empty, for the term.
	 */
	protected List<String> getArchivedTermSiteIds(String termId, Subset page, String institutionCode)
	{
		List<String> siteIds = new ArrayList<String>();
		if (this.recorder != null)
		{
			List<ArchiveDescription> archives = this.recorder.getTermArchives(termId, page, institutionCode);
			for (ArchiveDescription archive : archives)
			{
				siteIds.add(archive.getSiteId());
			}
		}

		return siteIds;
	}

	/**
	 * Get the user ids of all users who have no site membership, other than possibly their own myworkspace
	 * 
	 * @param limit
	 *        Return the first n qualifying user, or if null, all.
	 * @return The List of inactive user ids, possibly empty.
	 */
	@SuppressWarnings("unchecked")
	protected List<String> getInactiveUserIds(Integer limit)
	{
		String sql = null;
		Object[] fields = null;
		if ((limit == null) || (limit.intValue() == 0))
		{
			sql = "SELECT USER_ID, REALM_ID, COUNT, ARCHIVES FROM ("
					+ " SELECT U.USER_ID AS USER_ID, R.REALM_ID AS REALM_ID, COUNT(*) AS COUNT, A.ARCHIVES_ID AS ARCHIVES"
					+ " FROM SAKAI_USER U LEFT OUTER JOIN SAKAI_REALM_RL_GR G ON U.USER_ID = G.USER_ID"
					+ " LEFT OUTER JOIN SAKAI_REALM R ON G.REALM_KEY = R.REALM_KEY"
					+ " LEFT OUTER JOIN ARCHIVES_OWNERS A ON U.USER_ID = A.USER_ID"
					+ " GROUP BY U.USER_ID"
					+ " ) D WHERE D.COUNT = 1 AND USER_ID NOT IN ('postmaster', 'admin', 'helpdesk', 'resetpw') AND (D.REALM_ID IS NULL OR D.REALM_ID LIKE '/site/~%') AND ARCHIVES IS NULL ORDER BY D.USER_ID ASC";
		}
		else
		{
			sql = "SELECT USER_ID, REALM_ID, COUNT, ARCHIVES FROM ("
					+ " SELECT U.USER_ID AS USER_ID, R.REALM_ID AS REALM_ID, COUNT(*) AS COUNT, A.ARCHIVES_ID AS ARCHIVES"
					+ " FROM SAKAI_USER U LEFT OUTER JOIN SAKAI_REALM_RL_GR G ON U.USER_ID = G.USER_ID"
					+ " LEFT OUTER JOIN SAKAI_REALM R ON G.REALM_KEY = R.REALM_KEY"
					+ " LEFT OUTER JOIN ARCHIVES_OWNERS A ON U.USER_ID = A.USER_ID"
					+ " GROUP BY U.USER_ID"
					+ " ) D WHERE D.COUNT = 1 AND USER_ID NOT IN ('postmaster', 'admin', 'helpdesk', 'resetpw') AND (D.REALM_ID IS NULL OR D.REALM_ID LIKE '/site/~%') AND ARCHIVES IS NULL ORDER BY D.USER_ID ASC LIMIT ?";
			fields = new Object[1];
			fields[0] = limit;
		}

		List<String> rv = this.sqlService.dbRead(sql.toString(), fields, new SqlReader()
		{
			public Object readSqlResultRecord(ResultSet result)
			{
				try
				{
					String id = StringUtil.trimToNull(result.getString(1));
					return id;
				}
				catch (SQLException e)
				{
					M_log.warn("getInactiveUserIds: " + e.toString());
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
	 * Get the site ids for the term - those that still exist (have not been purged).
	 * 
	 * @param termId
	 *        The term id.
	 * @param page
	 *        The subset of the term to get.
	 * @param institutionCode
	 *        The institution code (lower case) to limit the action to - ignore if null. if the user is not permitted.
	 * @return The List of site ids, possibly empty, for the term.
	 */
	protected List<String> getTermSiteIds(String termId, Subset page, String institutionCode)
	{
		List<String> siteIds = new ArrayList<String>();
		if (this.termHandler != null) siteIds.addAll(this.termHandler.getTermSiteIds(termId, page, institutionCode));

		return siteIds;
	}

	/**
	 * Put out a message made up of some number of components, to the task status and log.
	 * 
	 * @param components
	 *        The message components
	 */
	protected void message(Object... components)
	{
		messageNoLog(components);

		// and log
		M_log.info(this.taskStatusBuf.toString());
	}

	/**
	 * Put out a message made up of some number of components, to the task status; add to what is there.
	 * 
	 * @param components
	 *        The message components
	 */
	protected void messageAppendNoLog(Object... components)
	{
		synchronized (this.taskSync)
		{
			// append each component
			for (Object o : components)
			{
				this.taskStatusBuf.append(o.toString());
			}
		}
	}

	/**
	 * Put out a message made up of some number of components, to the task status.
	 * 
	 * @param components
	 *        The message components
	 */
	protected void messageNoLog(Object... components)
	{
		synchronized (this.taskSync)
		{
			// start clean
			this.taskStatusBuf.setLength(0);

			// append each component
			for (Object o : components)
			{
				if (o == null)
				{
					this.taskStatusBuf.append("null");
				}
				else
				{
					this.taskStatusBuf.append(o.toString());
				}
			}
		}
	}

	/**
	 * Remove our security advisor.
	 */
	protected void popAdvisor()
	{
		this.securityService.popAdvisor();
	}

	/**
	 * Setup a security advisor.
	 */
	protected void pushAdvisor()
	{
		// setup a security advisor
		this.securityService.pushAdvisor(new SecurityAdvisor()
		{
			public SecurityAdvice isAllowed(String userId, String function, String reference)
			{
				return SecurityAdvice.ALLOWED;
			}
		});
	}

	/**
	 * Give up some time for other things.
	 */
	protected void sleep(int count)
	{
		try
		{
			// every 100, take a long nap
			if ((count % 100) == 0)
			{
				Thread.sleep(SLEEPY_TIME_LONG);
			}
			else
			{
				Thread.sleep(SLEEPY_TIME);
			}
		}
		catch (InterruptedException e)
		{
		}
	}

	/**
	 * @return The HomePageService, via the component manager.
	 */
	private HomePageService homePageService()
	{
		return (HomePageService) ComponentManager.get(HomePageService.class);
	}

	/**
	 * @return The SessionManager, via the component manager.
	 */
	private SessionManager sessionManager()
	{
		return (SessionManager) ComponentManager.get(SessionManager.class);
	}
}
