/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveDescriptionImpl.java $
 * $Id: ArchiveDescriptionImpl.java 2827 2012-04-05 02:06:31Z ggolden $
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

import java.util.Date;

import org.etudes.archives.api.ArchiveDescription;

/**
 * ArchiveDescriptionImpl implements ArchiveDescriptions
 */
public class ArchiveDescriptionImpl implements ArchiveDescription
{
	/** The archived date. */
	protected Date date = null;

	/** The archives id. */
	protected Long id = null;

	/** The site id archived. */
	protected String siteId = null;

	/** The site's term id. */
	protected Long term = null;

	/** The term description. */
	protected String termDescription = null;

	/** The site's title. */
	protected String title = null;

	/**
	 * @inheritDoc
	 */
	public boolean equals(Object obj)
	{
		if (obj instanceof ArchiveDescription)
		{
			return ((ArchiveDescription) obj).getId().equals(getId());
		}

		// compare to strings as id
		if (obj instanceof String)
		{
			return ((String) obj).equals(Long.toString(getId()));
		}

		return false;
	}

	/**
	 * {@inheritDoc}
	 */
	public Date getDate()
	{
		return this.date;
	}

	/**
	 * {@inheritDoc}
	 */
	public Long getId()
	{
		return this.id;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getSiteId()
	{
		return this.siteId;
	}

	/**
	 * {@inheritDoc}
	 */
	public Long getTerm()
	{
		return this.term;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getTermDescription()
	{
		return this.termDescription;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getTitle()
	{
		return this.title;
	}

	/**
	 * @inheritDoc
	 */
	public int hashCode()
	{
		return getId().hashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	public boolean hasTool(String commonToolId)
	{
		// TODO:
		return true;
	}

	/**
	 * Set the date.
	 * 
	 * @param date
	 *        The archive date.
	 */
	protected void setDate(Date date)
	{
		this.date = date;
	}

	/**
	 * Set the id.
	 * 
	 * @param id
	 *        The archive id.
	 */
	protected void setId(Long id)
	{
		this.id = id;
	}

	/**
	 * Set the site id.
	 * 
	 * @param siteId
	 *        The site id.
	 */
	protected void setSiteId(String siteId)
	{
		this.siteId = siteId;
	}

	/**
	 * Set the site term id.
	 * 
	 * @param term
	 *        The site term id.
	 */
	protected void setTerm(Long term)
	{
		this.term = term;
	}

	/**
	 * Set the site term's human readable description.
	 * 
	 * @param description
	 *        The site term description.
	 */
	protected void setTermDescription(String description)
	{
		this.termDescription = description;
	}

	/**
	 * Set the site title.
	 * 
	 * @param title
	 *        The site title.
	 */
	protected void setTitle(String title)
	{
		this.title = title;
	}
}
