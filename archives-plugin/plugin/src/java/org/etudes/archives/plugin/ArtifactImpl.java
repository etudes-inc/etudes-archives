/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArtifactImpl.java $
 * $Id: ArtifactImpl.java 2823 2012-04-03 20:57:39Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009 Etudes, Inc.
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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Artifact;

/**
 * ArchiverHandler
 */
public class ArtifactImpl implements Artifact
{
	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArtifactImpl.class);

	/** File extension number. */
	protected int fileNum = 1;

	/** File name in archives. */
	protected String fName = null;

	/** Id. */
	protected Long id = null;

	/** Properties. */
	protected Map<String, Object> properties = new HashMap<String, Object>();

	/** Reference string for to this artifact from the source system. */
	protected String reference = null;

	/** The references.  Note: this type of set preserves insertion order */
	protected Set<String> references = new LinkedHashSet<String>();

	/** Artifact type. */
	protected String type = null;

	/**
	 * {@inheritDoc}
	 */
	public String getFileName()
	{
		return this.fName;
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
	public String getNextFileSuffix()
	{
		return "-file-" + this.fileNum++;
	}

	/**
	 * {@inheritDoc}
	 */
	public Map<String, Object> getProperties()
	{
		return this.properties;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getReference()
	{
		return this.reference;
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<String> getReferences()
	{
		return this.references;
	}

	/**
	 * {@inheritDoc}
	 */
	public String getType()
	{
		return this.type;
	}

	/**
	 * Set the archives file name for this artifact (relative to the archive)
	 * 
	 * @param fName
	 *        The relative file name.
	 */
	public void setFileName(String fName)
	{
		this.fName = fName;
	}

	/**
	 * Set the id.
	 * 
	 * @param id
	 *        The artifact id.
	 */
	public void setId(long id)
	{
		this.id = Long.valueOf(id);
	}

	/**
	 * {@inheritDoc}
	 */
	public void setReference(String reference)
	{
		this.reference = reference;
	}

	/**
	 * {@inheritDoc}
	 */
	public void setType(String type)
	{
		this.type = type;
	}
}
