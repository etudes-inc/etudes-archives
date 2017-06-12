/**********************************************************************************
 * $URL: https://source.etudes.org/svn/apps/archives/trunk/archives-plugin/plugin/src/java/org/etudes/archives/plugin/ArchiveImpl.java $
 * $Id: ArchiveImpl.java 5915 2013-09-11 04:51:56Z ggolden $
 ***********************************************************************************
 *
 * Copyright (c) 2009, 2010, 2011, 2012, 2013 Etudes, Inc.
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.etudes.archives.api.Archive;
import org.etudes.archives.api.Artifact;
import org.etudes.util.api.Translation;

// TODO: write directly to a zip file

/**
 * ArchiverHandler
 */
public class ArchiveImpl implements Archive
{
	protected class Info
	{
		String key;

		int next;

		String type;

		Object value;
	}

	// Note on types: S-string, [-array string, M-map, C-collection of string, T-set string, L-Long, I-Integer, B-Boolean, F-Float, X-collection of
	// map Z-file

	/** Our log. */
	private static Log M_log = LogFactory.getLog(ArchiveImpl.class);

	/** artifact id generator. */
	protected long artifactId = 1;

	/** The artifacts. */
	protected List<Artifact> artifacts = new ArrayList<Artifact>();

	/** The file path to our root directory. */
	protected String filePath = null;

	/** The references from all the artifacts in the archive. Note: this type of set preserves insertion order */
	protected Set<String> references = new LinkedHashSet<String>();

	/** The archive's site id. */
	protected String siteId = null;

	/** Translations. */
	protected Set<Translation> translations = new HashSet<Translation>();

	/**
	 * {@inheritDoc}
	 */
	public void archive(Artifact artifact)
	{
		// write this out
		write((ArtifactImpl) artifact);

		// collect the references
		this.references.addAll(artifact.getReferences());

		// add to the manifest
		this.artifacts.add((ArtifactImpl) artifact);
	}

	/**
	 * {@inheritDoc}
	 */
	public void complete()
	{
		// create the manifest
		writeManifest();

		// zip up the folder
		writeZip(this.filePath);

		// remove the unzip folder
		File dir = new File(this.filePath);
		clear(dir);
	}

	/**
	 * {@inheritDoc}
	 */
	public List<Artifact> getArtifacts()
	{
		return this.artifacts;
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
	public String getSiteId()
	{
		return this.siteId;
	}

	/**
	 * {@inheritDoc}
	 */
	public Set<Translation> getTranslations()
	{
		return this.translations;
	}

	/**
	 * {@inheritDoc}
	 */
	public void init()
	{
		// create the root directory for the archive
		File dir = new File(this.filePath);

		if (dir.exists())
		{
			// if exists, clear it out
			clear(dir);
		}

		// make sure it exists
		dir.mkdirs();
	}

	/**
	 * {@inheritDoc}
	 */
	public Artifact newArtifact(String type, String reference)
	{
		ArtifactImpl rv = new ArtifactImpl();
		rv.setReference(reference);
		rv.setType(type);
		rv.setId(this.artifactId++);

		return rv;
	}

	/**
	 * {@inheritDoc}
	 */
	public void read()
	{
		// build artifacts from the manifest
		readManifest();

		// read in the attributes of each artifact
		for (Artifact artifact : this.artifacts)
		{
			readArtifact(artifact);
		}
	}

	/**
	 * {@inheritDoc}
	 */
	public byte[] readFile(String name, Integer size)
	{
		if ((name == null) || (size == null)) return null;

		InputStream in = null;
		ZipFile zip = null;

		// the name of the archive zip file
		String zipName = this.filePath.substring(0, this.filePath.length() - 1) + ".zip";
		try
		{
			// open the archive
			zip = new ZipFile(zipName);

			// find the artifact
			ZipEntry entry = zip.getEntry(name);
			if (entry != null)
			{
				int offset = 0;
				byte[] rv = new byte[size.intValue()];
				in = zip.getInputStream(entry);
				do
				{
					int lenRead = in.read(rv, offset, rv.length - offset);
					if (lenRead == -1) break;
					offset += lenRead;
				}
				while (offset < rv.length);
				return rv;
			}
		}
		catch (IOException e)
		{
			M_log.warn("readFile: " + e);
		}
		finally
		{
			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					M_log.warn("readFile/close in: " + e);
				}

				try
				{
					zip.close();
				}
				catch (IOException e)
				{
					M_log.warn("readFile/close zip: " + e);
				}
			}
		}

		return null;
	}

	/**
	 * Set the file path to the root directory.
	 * 
	 * @param filePath
	 *        The file path.
	 */
	public void setFilePath(String filePath)
	{
		this.filePath = filePath;
	}

	/**
	 * Set the site id
	 * 
	 * @param siteId
	 *        The site id.
	 */
	public void setSiteId(String id)
	{
		this.siteId = id;
	}

	/**
	 * Clear the way for the archives folder - delete the folder or file that is there.
	 * 
	 * @param loc
	 *        The file location we want to make clear.
	 */
	protected void clear(File loc)
	{
		if (loc.isDirectory())
		{
			File[] files = loc.listFiles();
			for (File f : files)
			{
				f.delete();
			}
		}

		loc.delete();
	}

	/**
	 * Compose a key and value, where the value is a collection of strings.
	 * 
	 * @param key
	 *        The key.
	 * @param value
	 *        The collection.
	 * @param artifact
	 *        The artifact.
	 * @return The composed collection.
	 */
	@SuppressWarnings("unchecked")
	protected String composeCollection(String key, Collection<Object> value, Artifact artifact)
	{
		if (value.isEmpty()) return "";

		// put the values into a string
		StringBuilder buf = new StringBuilder();
		buf.append(Integer.toString(value.size()));
		buf.append(":");
		String type = "C";
		for (Object o : value)
		{
			if (o instanceof String)
			{
				buf.append(composeString(null, (String) o, null));
			}

			else if (o instanceof Map)
			{
				type = "X";
				buf.append(composeMap(null, (Map<String, Object>) o, artifact));
			}

			else
			{
				M_log.warn("composeCollection: unknown type: key: " + key + " value: " + o.getClass());
			}
		}

		return composeString(key, buf.toString(), type);
	}

	/**
	 * Compose a key and value, where the value is a map of strings, string[]s, maps or collections.
	 * 
	 * @param masterKey
	 *        The key.
	 * @param map
	 *        The map.
	 * @param artifact
	 *        The artifact.
	 * @return The composed map.
	 */
	@SuppressWarnings("unchecked")
	protected String composeMap(String masterKey, Map<String, Object> map, Artifact artifact)
	{
		StringBuilder buf = new StringBuilder();
		for (Map.Entry<String, Object> entry : map.entrySet())
		{
			String key = entry.getKey();
			Object value = entry.getValue();

			// skip any null values
			if (value == null)
				continue;

			// collection handling
			else if (value instanceof Map)
			{
				buf.append(composeMap(key, (Map<String, Object>) value, artifact));
			}

			// collection handling
			else if (value instanceof Collection)
			{
				buf.append(composeCollection(key, (Collection<Object>) value, artifact));
			}

			else if (value instanceof String[])
			{
				buf.append(composeStrings(key, (String[]) value));
			}

			// stream handling
			else if (value instanceof InputStream)
			{
				buf.append(composeStream(key, (InputStream) value, artifact));
			}

			// otherwise treat it as a string
			else if (value instanceof String)
			{
				buf.append(composeString(key, (String) value, "S"));
			}

			else if (value instanceof Long)
			{
				buf.append(composeString(key, value.toString(), "L"));
			}

			else if (value instanceof Integer)
			{
				buf.append(composeString(key, value.toString(), "I"));
			}

			else if (value instanceof Boolean)
			{
				buf.append(composeString(key, value.toString(), "B"));
			}

			else if (value instanceof Float)
			{
				buf.append(composeString(key, value.toString(), "F"));
			}

			else
			{
				M_log.warn("composeMap: unknown type: key: " + key + " value: " + value);
			}
		}

		return composeString(masterKey, buf.toString(), "M");
	}

	/**
	 * Compose a key and value, where the value is a set of strings.
	 * 
	 * @param key
	 *        The key.
	 * @param value
	 *        The collection.
	 * @return The composed collection.
	 */
	protected String composeSet(String key, Set<String> value)
	{
		if (value.isEmpty()) return "";

		// put the values into a string
		StringBuilder buf = new StringBuilder();
		buf.append(Integer.toString(value.size()));
		buf.append(":");
		for (Object c : value)
		{
			String str = c.toString();
			buf.append(Integer.toString(str.length()));
			buf.append(":");
			buf.append(str);
		}

		return composeString(key, buf.toString(), "T");
	}

	/**
	 * Compose a key and value, where the value is a stream to end up in a separate file; the key value has the file name.
	 * 
	 * @param key
	 *        The key.
	 * @param in
	 *        The stream.
	 * @param artifact
	 *        The artifact.
	 * @return the composed string.
	 */
	protected String composeStream(String key, InputStream in, Artifact artifact)
	{
		if (in == null) return "";

		String fName = this.filePath + artifact.getFileName() + artifact.getNextFileSuffix();

		// stream to a file
		writeFile(in, fName);

		StringBuilder buf = new StringBuilder();

		// the length of our key and value (the relative file name)
		fName = fName.substring(fName.lastIndexOf("/") + 1);
		int len = key.length() + 1 + fName.length() + 2;

		// write the length as characters
		buf.append(Integer.toString(len));
		buf.append(":");

		// write the key
		buf.append(key);
		buf.append(":");

		// write the type
		buf.append("Z:");

		// write the value
		buf.append(fName);

		return buf.toString();
	}

	/**
	 * Compose a string for output
	 * 
	 * @param key
	 *        The key.
	 * @param value
	 *        The string.
	 * @return The composed string.
	 */
	protected String composeString(String key, String value, String type)
	{
		// Note: we don't want to trim - we want to reproduce the content exactly. -ggolden
		// value = value.trim();
		if (value.length() == 0) return "";

		StringBuilder buf = new StringBuilder();

		// the length of our key and type and value
		int len = value.length();
		if (key != null)
		{
			len += key.length() + 1 + type.length() + 1;
		}

		// write the length
		buf.append(Integer.toString(len));
		buf.append(":");

		// write the key and type
		if (key != null)
		{
			buf.append(key);
			buf.append(":");

			buf.append(type);
			buf.append(":");
		}

		// write the value
		buf.append(value);

		return buf.toString();
	}

	/**
	 * Compose a key and value, where the value is an array of strings.
	 * 
	 * @param out
	 *        The writer.
	 * @param key
	 *        The key.
	 * @param value
	 *        The String[].
	 */
	protected String composeStrings(String key, String[] value)
	{
		if (value.length == 0) return "";

		// for the value
		StringBuilder buf = new StringBuilder();

		// the number of items
		buf.append(Integer.toString(value.length));
		buf.append(":");

		for (String str : value)
		{
			if (str == null)
			{
				buf.append("N:");
			}
			else
			{
				buf.append(Integer.toString(str.length()));
				buf.append(":");
				buf.append(str);
			}
		}

		return composeString(key, buf.toString(), "[");
	}

	/**
	 * Pull a collection of maps from the source string.
	 * 
	 * @param source
	 *        The source string.
	 * @return A collection of maps.
	 */
	protected Collection<Map<String, Object>> decomposeCollectionMap(String source)
	{
		// take up to the ":" as count of maps
		int i = source.indexOf(':');
		String str = source.substring(0, i);
		int count = Integer.parseInt(str);
		Collection<Map<String, Object>> collection = new ArrayList<Map<String, Object>>();

		// take each map
		Info inner = new Info();
		inner.next = i + 1;
		while (count-- > 0)
		{
			// get the string holding the map
			inner = this.decomposeString(source, inner.next, false);

			Map<String, Object> map = decomposeMap((String) inner.value);
			collection.add(map);
		}

		return collection;
	}

	/**
	 * Pull a set out of the composed buffer.
	 * 
	 * @param source
	 *        The source buffer.
	 * @param pos
	 *        The starting position
	 * @return an Info with the key, set in value, and next position in source.
	 */
	protected Map<String, Object> decomposeMap(String source)
	{
		Map<String, Object> rv = new HashMap<String, Object>();

		Info info = new Info();
		info.next = 0;
		while (true)
		{
			info = decomposeString(source, info.next, true);
			if (info.key == null) break;

			// type specific processing
			if (info.type.equals("["))
			{
				// string array, staring with a count
				info.value = decomposeStrings((String) info.value);
			}
			else if (info.type.equals("C"))
			{
				// Collection of strings
				String[] strs = decomposeStrings((String) info.value);
				Collection<String> collection = new ArrayList<String>();
				for (String s : strs)
				{
					collection.add(s);
				}
				info.value = collection;
			}
			else if (info.type.equals("M"))
			{
				// Map
				Map<String, Object> map = decomposeMap((String) info.value);
				info.value = map;
			}
			else if (info.type.equals("T"))
			{
				// Set of strings
				String[] strs = decomposeStrings((String) info.value);
				Set<String> set = new HashSet<String>();
				for (String s : strs)
				{
					set.add(s);
				}
				info.value = set;
			}
			else if (info.type.equals("X"))
			{
				// Collection of Map
				Collection<Map<String, Object>> collection = decomposeCollectionMap((String) info.value);
				info.value = collection;
			}
			else if (info.type.equals("I"))
			{
				// Integer
				info.value = Integer.valueOf((String) info.value);
			}
			else if (info.type.equals("B"))
			{
				// Boolean
				info.value = Boolean.valueOf((String) info.value);
			}
			else if (info.type.equals("F"))
			{
				// Float
				info.value = Float.valueOf((String) info.value);
			}
			else if (info.type.equals("L"))
			{
				// Long
				info.value = Long.valueOf((String) info.value);
			}

			rv.put(info.key, info.value);
		}

		return rv;
	}

	/**
	 * Pull a set out of the composed buffer.
	 * 
	 * @param source
	 *        The source buffer.
	 * @param pos
	 *        The starting position
	 * @return an Info with the key, set in value, and next position in source.
	 */
	protected Info decomposeSet(String source, int pos)
	{
		// get the next string - sets the key and next
		Info rv = decomposeString(source, pos, true);

		// pull the set out of the value - first the count
		source = (String) rv.value;
		int i = source.indexOf(':');
		String str = source.substring(0, i);
		pos = i + 1;
		int count = Integer.parseInt(str);

		// each value
		Set<String> set = new HashSet<String>();
		while (count > 0)
		{
			// length
			i = source.indexOf(':', pos);
			str = source.substring(pos, i);
			pos = i + 1;
			int len = Integer.parseInt(str);

			str = source.substring(pos, pos + len);
			pos += len;

			set.add(str);
			count--;
		}

		// replace value with the set
		rv.value = set;

		return rv;
	}

	/**
	 * Pull a string out of the reader.
	 * 
	 * @param in
	 *        The reader.
	 * @return an Info with the key, string in value, and next position in source.
	 */
	protected Info decomposeString(BufferedReader in) throws IOException
	{
		Info rv = new Info();
		rv.key = null;
		rv.value = null;
		StringBuilder buf = new StringBuilder();

		// take up to the ":" as length of the following: key : type : value
		while (true)
		{
			int next = in.read();
			if ((next == -1) || (next == ':')) break;

			buf.append((char) next);
		}
		if (buf.length() == 0) return rv;
		int len = Integer.parseInt(buf.toString());
		buf.setLength(0);

		// take the key
		while (true)
		{
			int next = in.read();
			if ((next == -1) || (next == ':')) break;

			buf.append((char) next);
		}
		rv.key = buf.toString();
		buf.setLength(0);

		// take the type
		while (true)
		{
			int next = in.read();
			if ((next == -1) || (next == ':')) break;

			buf.append((char) next);
		}
		rv.type = buf.toString();
		buf.setLength(0);

		// take the value, len - key.length() bytes
		while (buf.length() < len - (rv.key.length() + 1 + rv.type.length() + 1))
		{
			int next = in.read();
			if (next == -1) break;

			buf.append((char) next);
		}
		rv.value = buf.toString();

		return rv;
	}

	/**
	 * Pull a string out of the composed buffer.
	 * 
	 * @param source
	 *        The source buffer.
	 * @param pos
	 *        The starting position
	 * @param keyAndTypeExpected
	 *        true if a key and type is expected, false if not.
	 * @return an Info with the key, set in value, and next position in source.
	 */
	protected Info decomposeString(String source, int pos, boolean keyAndTypeExpected)
	{
		Info rv = new Info();
		rv.key = null;
		rv.value = null;
		rv.type = null;
		if (pos >= source.length()) return rv;

		// take up to the ":" as length of the following: key : type : value
		int i = source.indexOf(':', pos);
		String str = source.substring(pos, i);
		pos = i + 1;
		int len = Integer.parseInt(str);

		if (keyAndTypeExpected)
		{
			// take the key
			i = source.indexOf(':', pos);
			rv.key = source.substring(pos, i);
			pos = i + 1;

			// take the type
			i = source.indexOf(':', pos);
			rv.type = source.substring(pos, i);
			pos = i + 1;

			// take the value, len - key and type length bytes
			rv.value = source.substring(pos, pos + len - (rv.key.length() + 1 + rv.type.length() + 1));

			// the next character to process
			rv.next = pos + len - (rv.key.length() + 1 + rv.type.length() + 1);
		}

		else
		{
			rv.key = "";
			rv.type = "";

			// take the value
			rv.value = source.substring(pos, pos + len);

			// the next character to process
			rv.next = pos + len;
		}

		return rv;
	}

	/**
	 * Pull a string array from a source string.
	 * 
	 * @param source
	 *        The source string.
	 * @return The string array.
	 */
	protected String[] decomposeStrings(String source)
	{
		// take up to the ":" as count of items
		int i = source.indexOf(':');
		String str = source.substring(0, i);
		int pos = i + 1;
		int count = Integer.parseInt(str);

		String[] rv = new String[count];
		for (int index = 0; index < count; index++)
		{
			// the length of the string
			i = source.indexOf(':', pos);
			str = source.substring(pos, i);
			pos = i + 1;
			int len = 0;
			if ("N".equals(str))
			{
				// the string is null
				rv[index] = null;
			}
			else
			{
				len = Integer.parseInt(str);

				// the string
				rv[index] = source.substring(pos, pos + len);
			}

			pos = pos + len;
		}

		return rv;
	}

	/**
	 * Read in the properties of the artifact.
	 * 
	 * @param artifact
	 *        The artifact.
	 */
	protected void readArtifact(Artifact artifact)
	{
		BufferedReader in = null;
		ZipFile zip = null;

		// the name of the archive zip file
		String zipName = this.filePath.substring(0, this.filePath.length() - 1) + ".zip";
		try
		{
			// open the archive
			zip = new ZipFile(zipName);

			// find the artifact
			ZipEntry entry = zip.getEntry(artifact.getFileName());
			if (entry != null)
			{
				in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), "UTF-8"));

				// read in the strings
				while (true)
				{
					Info info = decomposeString(in);
					if (info.key == null) break;

					// type specific processing
					if (info.type.equals("["))
					{
						// string array, staring with a count
						info.value = decomposeStrings((String) info.value);
					}
					else if (info.type.equals("C"))
					{
						// Collection of strings
						String[] strs = decomposeStrings((String) info.value);
						Collection<String> collection = new ArrayList<String>();
						for (String s : strs)
						{
							collection.add(s);
						}
						info.value = collection;
					}
					else if (info.type.equals("M"))
					{
						// Map
						Map<String, Object> map = decomposeMap((String) info.value);
						info.value = map;
					}
					else if (info.type.equals("T"))
					{
						// Set of strings
						String[] strs = decomposeStrings((String) info.value);
						Set<String> set = new HashSet<String>();
						for (String s : strs)
						{
							set.add(s);
						}
						info.value = set;
					}
					else if (info.type.equals("X"))
					{
						// Collection of Map
						Collection<Map<String, Object>> collection = decomposeCollectionMap((String) info.value);
						info.value = collection;
						// // take up to the ":" as count of maps
						// int i = ((String) info.value).indexOf(':');
						// String str = ((String) info.value).substring(0, i);
						// int count = Integer.parseInt(str);
						// Collection<Map<String, Object>> collection = new ArrayList<Map<String, Object>>();
						//
						// // take each map
						// Info inner = new Info();
						// inner.next = i + 1;
						// while (count-- > 0)
						// {
						// // get the string holding the map
						// inner = this.decomposeString((String) info.value, inner.next, false);
						//
						// Map<String, Object> map = decomposeMap((String) inner.value);
						// collection.add(map);
						// }
						// info.value = collection;
					}
					else if (info.type.equals("I"))
					{
						// Integer
						info.value = Integer.valueOf((String) info.value);
					}
					else if (info.type.equals("B"))
					{
						// Boolean
						info.value = Boolean.valueOf((String) info.value);
					}
					else if (info.type.equals("F"))
					{
						// Float
						info.value = Float.valueOf((String) info.value);
					}
					else if (info.type.equals("D"))
					{
						// Double
						info.value = Double.valueOf((String) info.value);
					}
					else if (info.type.equals("L"))
					{
						// Long
						info.value = Long.valueOf((String) info.value);
					}

					artifact.getProperties().put(info.key, info.value);
				}
			}
		}
		catch (IOException e)
		{
			M_log.warn("readArtifact: " + e);
		}
		finally
		{
			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					M_log.warn("readArtifact/close in: " + e);
				}

				try
				{
					zip.close();
				}
				catch (IOException e)
				{
					M_log.warn("readArtifact/close zip: " + e);
				}
			}
		}
	}

	/**
	 * Read a manifest.
	 */
	@SuppressWarnings("unchecked")
	protected void readManifest()
	{
		BufferedReader in = null;
		ZipFile zip = null;

		// the name of the archive zip file
		String zipName = this.filePath.substring(0, this.filePath.length() - 1) + ".zip";
		try
		{
			// open the archive
			zip = new ZipFile(zipName);

			// find the manifest
			ZipEntry entry = zip.getEntry("manifest");
			if (entry != null)
			{
				in = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), "UTF-8"));

				// each line is an artifact
				while (true)
				{
					String line = in.readLine();
					if (line == null) break;

					ArtifactImpl artifact = new ArtifactImpl();

					Info info = decomposeString(line, 0, true);
					artifact.setId(Long.valueOf((String) info.value));

					if (info.next < line.length())
					{
						info = decomposeString(line, info.next, true);
						artifact.setReference((String) info.value);
					}

					if (info.next < line.length())
					{
						info = decomposeString(line, info.next, true);
						artifact.setType((String) info.value);
					}

					if (info.next < line.length())
					{
						info = decomposeString(line, info.next, true);
						artifact.setFileName((String) info.value);
					}

					if (info.next < line.length())
					{
						info = decomposeSet(line, info.next);
						artifact.getReferences().addAll((Set<String>) info.value);
					}

					this.artifacts.add(artifact);

					// update the artifactId in case we generate more
					if (artifact.getId().longValue() >= this.artifactId)
					{
						this.artifactId = artifact.getId().longValue() + 1;
					}
				}
			}
		}
		catch (IOException e)
		{
			M_log.warn("readManifest: " + e);
		}
		finally
		{
			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					M_log.warn("readManifest/close in: " + e);
				}

				try
				{
					zip.close();
				}
				catch (IOException e)
				{
					M_log.warn("readManifest/close zip: " + e);
				}
			}
		}
	}

	/**
	 * Write the artifact to a file.
	 * 
	 * @param artifact
	 *        The artifact to write.
	 */
	@SuppressWarnings("unchecked")
	protected void write(ArtifactImpl artifact)
	{
		// the artifact's relative file name
		artifact.setFileName("artifact-" + artifact.getId().toString());

		// the file
		Writer out = null;
		String artifactFileName = this.filePath + artifact.getFileName();
		try
		{
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(artifactFileName), "UTF-8"));
			for (Map.Entry<String, Object> entry : artifact.getProperties().entrySet())
			{
				String key = entry.getKey();
				Object value = entry.getValue();

				// skip any null values
				if (value == null) continue;

				// stream handling
				if (value instanceof InputStream)
				{
					out.write(composeStream(key, (InputStream) value, artifact));
				}

				// map handling
				else if (value instanceof Map)
				{
					out.write(composeMap(key, (Map<String, Object>) value, artifact));
				}

				// collection handling
				else if (value instanceof Collection)
				{
					out.write(composeCollection(key, (Collection<Object>) value, artifact));
				}

				// array handling (strings)
				else if (value instanceof String[])
				{
					out.write(composeStrings(key, (String[]) value));
				}

				// otherwise treat it as a string
				else if (value instanceof String)
				{
					out.write(composeString(key, (String) value, "S"));
				}

				else if (value instanceof Long)
				{
					out.write(composeString(key, value.toString(), "L"));
				}

				else if (value instanceof Integer)
				{
					out.write(composeString(key, value.toString(), "I"));
				}

				else if (value instanceof Boolean)
				{
					out.write(composeString(key, value.toString(), "B"));
				}

				else if (value instanceof Float)
				{
					out.write(composeString(key, value.toString(), "F"));
				}

				else if (value instanceof Double)
				{
					out.write(composeString(key, value.toString(), "D"));
				}

				else
				{
					M_log.warn("write: unknown type: key: " + key + " value: " + value.getClass());
				}

			}
		}
		catch (IOException e)
		{
			M_log.warn("write: " + e);
		}
		finally
		{
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					M_log.warn("write/close: " + e);
				}
			}
		}
	}

	/**
	 * Write a file at path with the contents of the input stream.
	 * 
	 * @param in
	 *        The content stream.
	 * @param path
	 *        The full file name.
	 */
	protected void writeFile(InputStream in, String path)
	{
		FileOutputStream out = null;
		try
		{
			out = new FileOutputStream(path);
			byte[] buffer = new byte[10000];
			while (true)
			{
				int len = in.read(buffer);
				if (len == -1) break;

				out.write(buffer, 0, len);
			}
		}
		catch (IOException e)
		{
			M_log.warn("writeFile: " + e);
		}
		finally
		{
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					M_log.warn("writeFile/close out: " + e);
				}
			}

			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					M_log.warn("writeFile/close in: " + e);
				}
			}
		}
	}

	/**
	 * Write the manifest to a file.
	 */
	protected void writeManifest()
	{
		// the file
		Writer out = null;
		String artifactFileName = this.filePath + "manifest";
		try
		{
			out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(artifactFileName), "UTF-8"));

			// write out a line for each artifact
			for (Artifact a : this.artifacts)
			{
				out.write(composeString("id", a.getId().toString(), "L"));
				out.write(composeString("ref", a.getReference(), "S"));
				out.write(composeString("type", a.getType(), "S"));
				out.write(composeString("artifact", a.getFileName(), "S"));
				out.write(composeSet("refs", a.getReferences()));
				out.write("\n");
			}
		}
		catch (IOException e)
		{
			M_log.warn("writeManifest: " + e);
		}
		finally
		{
			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					M_log.warn("writeManifest/close: " + e);
				}
			}
		}
	}

	/**
	 * Create a zip of the folder, named the folder name with ".zip"
	 * 
	 * @param root
	 *        The folder path.
	 */
	protected void writeZip(String root)
	{
		ZipOutputStream out = null;
		FileInputStream in = null;

		try
		{
			// the name of the zip file
			String zipName = this.filePath.substring(0, this.filePath.length() - 1) + ".zip";

			// delete the file if present
			File zip = new File(zipName);
			if (zip.exists())
			{
				zip.delete();
			}

			// the zip file
			out = new ZipOutputStream(new FileOutputStream(zipName));

			// the archives folder
			File archives = new File(this.filePath);

			// zip up all the files in there
			File[] files = archives.listFiles();
			for (File f : files)
			{
				// get the file
				in = new FileInputStream(f);

				// add an entry in the zip
				out.putNextEntry(new ZipEntry(f.getName()));

				// read from the file into the zip
				byte[] buffer = new byte[10000];
				while (true)
				{
					int len = in.read(buffer);
					if (len == -1) break;

					out.write(buffer, 0, len);
				}

				// close the zip entry
				out.closeEntry();

				// close the file
				in.close();
				in = null;
			}
		}
		catch (IOException e)
		{
			M_log.warn("writeZip" + e);
		}
		finally
		{
			if (in != null)
			{
				try
				{
					in.close();
				}
				catch (IOException e)
				{
					M_log.warn("writeZip/close in" + e);
				}
			}

			if (out != null)
			{
				try
				{
					out.close();
				}
				catch (IOException e)
				{
					M_log.warn("writeZip/close out" + e);
				}
			}
		}
	}
}
