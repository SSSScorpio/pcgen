/*
 * Copyright 2007 (C) Tom Parker <thpr@users.sourceforge.net>
 * 
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */
package pcgen.rules.context;

import java.net.URI;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import pcgen.base.util.OneToOneMap;
import pcgen.cdom.base.CDOMObject;
import pcgen.cdom.base.CategorizedCDOMObject;
import pcgen.cdom.base.Category;
import pcgen.cdom.enumeration.ListKey;
import pcgen.cdom.enumeration.ObjectKey;
import pcgen.cdom.enumeration.StringKey;
import pcgen.cdom.enumeration.SubClassCategory;
import pcgen.cdom.enumeration.Type;
import pcgen.cdom.list.ClassSkillList;
import pcgen.cdom.list.ClassSpellList;
import pcgen.cdom.list.DomainSpellList;
import pcgen.cdom.reference.CDOMDirectSingleRef;
import pcgen.cdom.reference.CDOMGroupRef;
import pcgen.cdom.reference.CDOMSingleRef;
import pcgen.cdom.reference.ReferenceManufacturer;
import pcgen.cdom.reference.UnconstructedValidator;
import pcgen.core.AbilityCategory;
import pcgen.core.Domain;
import pcgen.core.PCClass;
import pcgen.core.SubClass;
import pcgen.util.Logging;

public abstract class AbstractReferenceContext implements ReferenceContext
{

	private static final Class<CategorizedCDOMObject> CATEGORIZED_CDOM_OBJECT_CLASS = CategorizedCDOMObject.class;
	private static final Class<DomainSpellList> DOMAINSPELLLIST_CLASS = DomainSpellList.class;
	private static final Class<ClassSkillList> CLASSSKILLLIST_CLASS = ClassSkillList.class;
	private static final Class<ClassSpellList> CLASSSPELLLIST_CLASS = ClassSpellList.class;
	private static final Class<SubClass> SUBCLASS_CLASS = SubClass.class;

	private final Map<Class<?>, OneToOneMap<CDOMObject, String>> abbMap = new HashMap<Class<?>, OneToOneMap<CDOMObject, String>>();

	private final HashMap<CDOMObject, CDOMSingleRef<?>> directRefCache = new HashMap<CDOMObject, CDOMSingleRef<?>>();

	public abstract <T extends CDOMObject> ReferenceManufacturer<T, ? extends CDOMSingleRef<T>> getManufacturer(
			Class<T> cl);

	/**
	 * Retrieve the Reference manufacturer that handles this class and category. Note that 
	 * even though abilities are categorized, the category may not be know initially, so 
	 * null cat values are legal.   
	 * 
	 * @param cl The class that is being processed (Ability, SubClass etc)
	 * @param cat The category of the class.
	 * @return The reference manufacturer
	 */
	protected abstract <T extends CDOMObject & CategorizedCDOMObject<T>> ReferenceManufacturer<T, ? extends CDOMSingleRef<T>> getManufacturer(
			Class<T> cl, Category<T> cat);

	public abstract Collection<? extends ReferenceManufacturer<? extends CDOMObject, ?>> getAllManufacturers();

	public boolean validate(UnconstructedValidator validator)
	{
		boolean returnGood = true;
		for (ReferenceManufacturer<?, ?> ref : getAllManufacturers())
		{
			returnGood &= ref.validate(validator);
		}
		return returnGood;
	}

	public <T extends CDOMObject> CDOMGroupRef<T> getCDOMAllReference(Class<T> c)
	{
		return getManufacturer(c).getAllReference();
	}

	public <T extends CDOMObject & CategorizedCDOMObject<T>> CDOMGroupRef<T> getCDOMAllReference(
			Class<T> c, Category<T> cat)
	{
		return getManufacturer(c, cat).getAllReference();
	}

	public <T extends CDOMObject> CDOMGroupRef<T> getCDOMTypeReference(
			Class<T> c, String... val)
	{
		return getManufacturer(c).getTypeReference(val);
	}

	public <T extends CDOMObject & CategorizedCDOMObject<T>> CDOMGroupRef<T> getCDOMTypeReference(
			Class<T> c, Category<T> cat, String... val)
	{
		return getManufacturer(c, cat).getTypeReference(val);
	}

	public <T extends CDOMObject> T constructCDOMObject(Class<T> c, String val)
	{
		T obj;
		if (CATEGORIZED_CDOM_OBJECT_CLASS.isAssignableFrom(c))
		{
			Class cl = c;
			obj = (T) getManufacturer(cl, (Category) null).constructObject(val);
		}
		else
		{
			obj = getManufacturer(c).constructObject(val);
		}
		obj.put(ObjectKey.SOURCE_URI, sourceURI);
		return obj;
	}

	public <T extends CDOMObject> void constructIfNecessary(Class<T> cl,
			String value)
	{
		getManufacturer(cl).constructIfNecessary(value);
	}

	public <T extends CDOMObject> CDOMSingleRef<T> getCDOMReference(Class<T> c,
			String val)
	{
		/*
		 * Keeping this generic (not inlined as the other methods in this class)
		 * is required by bugs in Sun's Java 5 compiler.
		 */
		ReferenceManufacturer manufacturer = getManufacturer(c);
		return manufacturer.getReference(val);
	}

	public <T extends CDOMObject & CategorizedCDOMObject<T>> CDOMSingleRef<T> getCDOMReference(
			Class<T> c, Category<T> cat, String val)
	{
		/*
		 * Keeping this generic (not inlined as the other methods in this class)
		 * is required by bugs in Sun's Java 5 compiler.
		 */
		ReferenceManufacturer manufacturer = getManufacturer(c, cat);
		return manufacturer.getReference(val);
	}

	public <T extends CDOMObject> void reassociateKey(String key, T obj)
	{
		if (CATEGORIZED_CDOM_OBJECT_CLASS.isAssignableFrom(obj.getClass()))
		{
			Class cl = obj.getClass();
			reassociateCategorizedKey(key, obj, cl);
		}
		else
		{
			getManufacturer((Class<T>) obj.getClass()).renameObject(key, obj);
		}
	}

	private <T extends CDOMObject & CategorizedCDOMObject<T>> void reassociateCategorizedKey(
			String key, CDOMObject orig, Class<T> cl)
	{
		T obj = (T) orig;
		getManufacturer(cl, obj.getCDOMCategory()).renameObject(key, obj);
	}

	public <T extends CDOMObject> T silentlyGetConstructedCDOMObject(
			Class<T> c, String val)
	{
		return getManufacturer(c).getActiveObject(val);
	}

	public <T extends CDOMObject & CategorizedCDOMObject<T>> T silentlyGetConstructedCDOMObject(
			Class<T> c, Category<T> cat, String val)
	{
		return getManufacturer(c, cat).getActiveObject(val);
	}

	// public <T extends CDOMObject & CategorizedCDOMObject<T>> CDOMSingleRef<T>
	// getCDOMReference(
	// Class<T> c, Category<T> cat, String val)
	// {
	// return categorized.getCDOMReference(c, cat, val);
	// }
	//
	
	public <T extends CDOMObject & CategorizedCDOMObject<T>> void reassociateCategory(
			Category<T> cat, T obj)
	{
		Category<T> oldCat = obj.getCDOMCategory();
		if (oldCat == null && cat == null || oldCat != null
				&& oldCat.equals(cat))
		{
			Logging.errorPrint("Worthless Category change encountered: "
					+ obj.getDisplayName() + " " + oldCat);
		}
		reassociateCategory((Class<T>) obj.getClass(), obj, oldCat, cat);
	}

	private <T extends CDOMObject & CategorizedCDOMObject<T>> void reassociateCategory(
			Class<T> cl, T obj, Category<T> oldCat, Category<T> cat)
	{
		getManufacturer(cl, oldCat).forgetObject(obj);
		obj.setCDOMCategory(cat);
		getManufacturer(cl, cat).addObject(obj, obj.getKeyName());
	}

	// public <T extends CDOMObject> T cloneConstructedCDOMObject(T orig,
	// String newKey)
	// {
	// Class cl = (Class) orig.getClass();
	// if (CategorizedCDOMObject.class.isAssignableFrom(cl))
	// {
	// return (T) cloneCategorized(cl, ((CategorizedCDOMObject) orig)
	// .getCDOMCategory(), orig, newKey);
	// }
	// else
	// {
	// return (T) simple.cloneConstructedCDOMObject(cl, orig, newKey);
	// }
	// }

	public <T extends CDOMObject> void importObject(T orig)
	{
		if (CATEGORIZED_CDOM_OBJECT_CLASS.isAssignableFrom(orig.getClass()))
		{
			Class cl = orig.getClass();
			importCategorized(orig, cl);
		}
		else
		{
			getManufacturer((Class<T>) orig.getClass()).addObject(orig,
					orig.getKeyName());
		}
	}

	private <T extends CDOMObject & CategorizedCDOMObject<T>> void importCategorized(
			CDOMObject orig, Class<T> cl)
	{
		T obj = (T) orig;
		getManufacturer(cl, obj.getCDOMCategory()).addObject(obj,
				obj.getKeyName());
	}

	public <T extends CDOMObject> boolean forget(T obj)
	{
		OneToOneMap<CDOMObject, String> map = abbMap.get(obj.getClass());
		if (map != null)
		{
			map.remove(obj);
		}

		if (CATEGORIZED_CDOM_OBJECT_CLASS.isAssignableFrom(obj.getClass()))
		{
			Class cl = obj.getClass();
			CategorizedCDOMObject cdo = (CategorizedCDOMObject) obj;
			return getManufacturer(cl, cdo.getCDOMCategory()).forgetObject(obj);
		}
		else
		{
			return getManufacturer((Class<T>) obj.getClass()).forgetObject(obj);
		}
	}

	// public <T extends CDOMObject & CategorizedCDOMObject<T>> T
	// cloneCategorized(
	// Class<T> cl, Category<T> cat, Object o, String newKey)
	// {
	// return categorized.cloneConstructedCDOMObject(cl, cat, (T) o, newKey);
	// }

	// public <T extends CDOMObject & CategorizedCDOMObject<T>>
	// ReferenceManufacturer<T, CDOMCategorizedSingleRef<T>>
	// getReferenceManufacturer(
	// Class<T> c, Category<T> cat)
	// {
	// return categorized.getManufacturer(c, cat);
	// }

	public <T extends CDOMObject> Collection<T> getConstructedCDOMObjects(
			Class<T> c)
	{
		// if (CategorizedCDOMObject.class.isAssignableFrom(c))
		// {
		// return categorized.getAllConstructedCDOMObjects((Class) c);
		// }
		// else
		// {
		return getManufacturer(c).getAllObjects();
		// }
	}

	// public <T extends CDOMObject & CategorizedCDOMObject<T>> Collection<T>
	// getConstructedCDOMObjects(
	// Class<T> c, Category<T> cat)
	// {
	// return categorized.getConstructedCDOMObjects(c, cat);
	// }

	public Set<CDOMObject> getAllConstructedObjects()
	{
		Set<CDOMObject> set = new HashSet<CDOMObject>();
		for (ReferenceManufacturer<? extends CDOMObject, ?> ref : getAllManufacturers())
		{
			set.addAll(ref.getAllObjects());
		}
		// Collection otherSet = categorized.getAllConstructedCDOMObjects();
		// set.addAll(otherSet);
		return set;
	}

	public <T extends CDOMObject> boolean containsConstructedCDOMObject(
			Class<T> c, String s)
	{
		return getManufacturer(c).containsObject(s);
	}

	public void buildDerivedObjects()
	{
		Collection<Domain> domains = getConstructedCDOMObjects(Domain.class);
		for (Domain d : domains)
		{
			DomainSpellList dsl = constructCDOMObject(DOMAINSPELLLIST_CLASS, d.getKeyName());
			dsl.addToListFor(ListKey.TYPE, Type.DIVINE);
			d.put(ObjectKey.DOMAIN_SPELLLIST, dsl);
		}
		Collection<PCClass> classes = getConstructedCDOMObjects(PCClass.class);
		for (PCClass pcc : classes)
		{
			String key = pcc.getKeyName();
			constructCDOMObject(CLASSSKILLLIST_CLASS, key);
			// TODO Need to limit which are built to only spellcasters...
			ClassSpellList csl = constructCDOMObject(CLASSSPELLLIST_CLASS, key);
			String spelltype = pcc.get(StringKey.SPELLTYPE);
			if (spelltype != null)
			{
				csl.addToListFor(ListKey.TYPE, Type.getConstant(spelltype));
			}
			pcc.put(ObjectKey.CLASS_SPELLLIST, csl);
			// simple.constructCDOMObject(SPELLPROGRESSION_CLASS, key);
			// Collection<CDOMSubClass> subclasses = categorized
			// .getConstructedCDOMObjects(SUBCLASS_CLASS, SubClassCategory
			// .getConstant(key));
			// for (CDOMSubClass subcl : subclasses)
			if (pcc.containsListFor(ListKey.SUB_CLASS))
			{
				SubClassCategory cat = SubClassCategory.getConstant(key);
				boolean needSelf = pcc.getSafe(ObjectKey.ALLOWBASECLASS);
				for (SubClass subcl : pcc.getListFor(ListKey.SUB_CLASS))
				{
					String subKey = subcl.getKeyName();
					if (subKey.equalsIgnoreCase(key))
					{
						needSelf = false;
					}
					constructCDOMObject(CLASSSKILLLIST_CLASS, subKey);
					// TODO Need to limit which are built to only
					// spellcasters...
					csl = constructCDOMObject(CLASSSPELLLIST_CLASS, subKey);
					if (spelltype != null)
					{
						csl.addToListFor(ListKey.TYPE, Type.getConstant(spelltype));
					}
					subcl.put(ObjectKey.CLASS_SPELLLIST, csl);
					// constructCDOMObject(SPELLPROGRESSION_CLASS, subKey);
					/*
					 * CONSIDER For right now, this is easiest to do here, though
					 * doing this 'live' may be more appropriate in the end.
					 */
					subcl.setCDOMCategory(cat);
					importObject(subcl);
				}
				if (needSelf)
				{
					SubClass self = constructCDOMObject(SUBCLASS_CLASS, key);
					reassociateCategory(SUBCLASS_CLASS, self, null, cat);
				}
			}
		}
	}

	public <T extends CDOMObject> CDOMSingleRef<T> getCDOMDirectReference(T obj)
	{
		CDOMSingleRef<?> ref = directRefCache.get(obj);
		if (ref == null)
		{
			ref = new CDOMDirectSingleRef<T>(obj);
		}
		return (CDOMSingleRef<T>) ref;
	}

	public void registerAbbreviation(CDOMObject obj, String value)
	{
		OneToOneMap<CDOMObject, String> map = abbMap.get(obj.getClass());
		if (map == null)
		{
			map = new OneToOneMap<CDOMObject, String>();
			abbMap.put(obj.getClass(), map);
		}
		map.put(obj, value);
		obj.put(StringKey.ABB, value);
	}

	public String getAbbreviation(CDOMObject obj)
	{
		OneToOneMap<CDOMObject, String> map = abbMap.get(obj.getClass());
		return map == null ? null : map.get(obj);
	}

	public <T> T getAbbreviatedObject(Class<T> cl, String value)
	{
		OneToOneMap<T, String> map = (OneToOneMap<T, String>) abbMap.get(cl);
		return map == null ? null : map.getKeyFor(value);
	}

	private URI sourceURI;

	private URI extractURI;

	public URI getExtractURI()
	{
		return extractURI;
	}

	public void setExtractURI(URI extractURI)
	{
		this.extractURI = extractURI;
	}

	public URI getSourceURI()
	{
		return sourceURI;
	}

	public void setSourceURI(URI sourceURI)
	{
		this.sourceURI = sourceURI;
	}

	public void resolveReferences()
	{
		for (ReferenceManufacturer<?, ?> rs : getAllManufacturers())
		{
			rs.resolveReferences();
		}
	}

	public void buildDeferredObjects()
	{
		for (ReferenceManufacturer<?, ?> rs : getAllManufacturers())
		{
			rs.buildDeferredObjects();
		}
	}

	public <T extends CDOMObject> T constructNowIfNecessary(Class<T> cl, String name)
	{
		return getManufacturer(cl).constructNowIfNecessary(name);
	}

	// public <T extends CDOMObject> CDOMAddressedSingleRef<T>
	// getAddressedReference(
	// CDOMObject obj, Class<T> name, String addressName)
	// {
	// CDOMAddressedSingleRef<T> addr = addressed.get(obj, name);
	// if (addr == null)
	// {
	// addr = new CDOMAddressedSingleRef<T>(obj, name, addressName);
	// addressed.put(obj, name, addr);
	// }
	// return addr;
	// }

}
