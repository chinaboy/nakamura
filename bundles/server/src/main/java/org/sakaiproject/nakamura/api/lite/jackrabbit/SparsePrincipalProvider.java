package org.sakaiproject.nakamura.api.lite.jackrabbit;

import java.security.Principal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.api.security.principal.PrincipalIterator;
import org.apache.jackrabbit.api.security.principal.PrincipalManager;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.core.security.AnonymousPrincipal;
import org.apache.jackrabbit.core.security.principal.AdminPrincipal;
import org.apache.jackrabbit.core.security.principal.ConcurrentLRUMap;
import org.apache.jackrabbit.core.security.principal.EveryonePrincipal;
import org.apache.jackrabbit.core.security.principal.PrincipalIteratorAdapter;
import org.apache.jackrabbit.core.security.principal.PrincipalProvider;
import org.sakaiproject.nakamura.api.lite.ClientPoolException;
import org.sakaiproject.nakamura.api.lite.Repository;
import org.sakaiproject.nakamura.api.lite.StorageClientException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessControlManager;
import org.sakaiproject.nakamura.api.lite.accesscontrol.AccessDeniedException;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Permissions;
import org.sakaiproject.nakamura.api.lite.accesscontrol.Security;
import org.sakaiproject.nakamura.api.lite.authorizable.Authorizable;
import org.sakaiproject.nakamura.api.lite.authorizable.AuthorizableManager;
import org.sakaiproject.nakamura.api.lite.authorizable.Group;
import org.sakaiproject.nakamura.api.lite.authorizable.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

public class SparsePrincipalProvider implements PrincipalProvider {

	private EveryonePrincipal everyonePrincipal;
	/** Option name for the max size of the cache to use */
	public static final String MAXSIZE_KEY = "cacheMaxSize";
	/** Option name to enable negative cache entries (see JCR-2672) */
	public static final String NEGATIVE_ENTRY_KEY = "cacheIncludesNegative";
	private static final Logger LOGGER = LoggerFactory
			.getLogger(SparsePrincipalProvider.class);

	/** flag indicating if the instance has not been {@link #close() closed} */
	private boolean initialized;

	/** the principal cache */
	private ConcurrentLRUMap<String, Principal> cache = new ConcurrentLRUMap<String, Principal>();
	private Repository sparseRepository;
	private org.sakaiproject.nakamura.api.lite.Session session;
	private AuthorizableManager authorizableManager;
	private AccessControlManager accesControlManager;

	/**
	 * Creates a new DefaultPrincipalProvider reading the principals from the
	 * storage below the given security root node.
	 * 
	 * @throws RepositoryException
	 *             if an error accessing the repository occurs.
	 */
	public SparsePrincipalProvider() throws RepositoryException {
		try {
			sparseRepository = SparseComponentHolder
					.getSparseRepositoryInstance();
			session = sparseRepository.loginAdministrative();
			authorizableManager = session.getAuthorizableManager();
			accesControlManager = session.getAccessControlManager();
			everyonePrincipal = EveryonePrincipal.getInstance();
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		} catch (AccessDeniedException e) {
			throw new RepositoryException(e.getMessage(), e);
		}

	}

	public boolean canReadPrincipal(Session session, Principal principal) {
		try {
			Authorizable authorizable = authorizableManager
					.findAuthorizable(session.getUserID());
			if (authorizable != null) {
				return accesControlManager.can(authorizable,
						Security.ZONE_AUTHORIZABLES, principal.getName(),
						Permissions.CAN_READ);
			}
		} catch (AccessDeniedException e) {
			LOGGER.warn(e.getMessage(), e);
		} catch (StorageClientException e) {
			LOGGER.warn(e.getMessage(), e);
		}
		return false;
	}

	public PrincipalIterator findPrincipals(String simpleFilter) {
		return findPrincipals(simpleFilter, PrincipalManager.SEARCH_TYPE_ALL);
	}

	@SuppressWarnings("unchecked")
	public PrincipalIterator findPrincipals(String simpleFilter, int searchType) {

		checkInitialized();
		try {
			switch (searchType) {
			case PrincipalManager.SEARCH_TYPE_GROUP:
				return new SparsePrincipalIterator(
						authorizableManager
								.findAuthorizable(
										"rep:principalName",
										simpleFilter,
										org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.class));
			case PrincipalManager.SEARCH_TYPE_NOT_GROUP:
				return new SparsePrincipalIterator(
						authorizableManager
								.findAuthorizable(
										"rep:principalName",
										simpleFilter,
										org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.class));
			case PrincipalManager.SEARCH_TYPE_ALL:
				return new SparsePrincipalIterator(
						authorizableManager
								.findAuthorizable(
										"rep:principalName",
										simpleFilter,
										org.sakaiproject.nakamura.api.lite.authorizable.Authorizable.class));
			default:
				throw new IllegalArgumentException("Invalid searchType");
			}
		} catch (StorageClientException e) {
			LOGGER.debug(e.getMessage(), e);
		}
		return new PrincipalIteratorAdapter(Collections.EMPTY_LIST);
	}

	public PrincipalIterator getGroupMembership(Principal principal) {
		final List<String> memberIds = new ArrayList<String>();
		try {
			org.sakaiproject.nakamura.api.lite.authorizable.Authorizable a = authorizableManager
					.findAuthorizable(principal.getName());
			if (a instanceof Group) {
				Collections.addAll(memberIds, ((Group) a).getMembers());
			}
		} catch (AccessDeniedException e) {
			LOGGER.debug(e.getMessage(), e);
		} catch (StorageClientException e) {
			LOGGER.debug(e.getMessage(), e);
		}
		if (everyonePrincipal.isMember(principal)
				&& !memberIds.contains(everyonePrincipal.getName())) {
			memberIds.add(everyonePrincipal.getName());
			addToCache(principal);
		}

		return new PrincipalIteratorAdapter(new Iterator<Principal>() {

			private int p;
			private Principal principal;

			public boolean hasNext() {
				while (p < memberIds.size()) {
					String id = memberIds.get(p);
					p++;
					try {
						if (everyonePrincipal.getName().equals(id)) {
							principal = everyonePrincipal;
							return true;
						} else {
							org.sakaiproject.nakamura.api.lite.authorizable.Authorizable a = authorizableManager
									.findAuthorizable(id);
							if (a instanceof org.sakaiproject.nakamura.api.lite.authorizable.Group) {
								for (String pid : a.getPrincipals()) {
									if (!memberIds.contains(pid)) {
										memberIds.add(pid);
									}
								}
								if (cache.containsKey(id)) {
									principal = cache.get(id);
								} else {
									principal = new SparsePrincipal(a, this
											.getClass().getName());
									addToCache(principal);
								}
								return true;
							} else if (a instanceof org.sakaiproject.nakamura.api.lite.authorizable.User) {
								if (cache.containsKey(id)) {
									principal = cache.get(id);
								} else {
									principal = new SparsePrincipal(a, this
											.getClass().getName());
									addToCache(principal);
								}
								return true;
							}
						}
					} catch (AccessDeniedException e) {
						LOGGER.debug(e.getMessage(), e);
					} catch (StorageClientException e) {
						LOGGER.debug(e.getMessage(), e);
					}
				}
				return false;
			}

			public Principal next() {
				return principal;
			}

			public void remove() {
				throw new UnsupportedOperationException();
			}
		});
	}

	/**
	 * @see PrincipalProvider#getPrincipals(int)
	 * @param searchType
	 *            Any of the following search types:
	 *            <ul>
	 *            <li>{@link PrincipalManager#SEARCH_TYPE_GROUP}</li>
	 *            <li>{@link PrincipalManager#SEARCH_TYPE_NOT_GROUP}</li>
	 *            <li>{@link PrincipalManager#SEARCH_TYPE_ALL}</li>
	 *            </ul>
	 * @see PrincipalProvider#getPrincipals(int)
	 */
	public PrincipalIterator getPrincipals(int searchType) {
		return findPrincipals(null, searchType);
	}

	/**
	 * {@inheritDoc}
	 * <p/>
	 * This implementation uses the user and node resolver to find the
	 * appropriate nodes.
	 * 
	 * @throws RepositoryException
	 */
	protected Principal providePrincipal(String principalName)
			throws RepositoryException {
		// check for 'everyone'
		if (everyonePrincipal.getName().equals(principalName)) {
			return everyonePrincipal;
		}
		if ( User.ADMIN_USER.equals(principalName)) {
			return new AdminPrincipal(User.ADMIN_USER);
		}
		if ( User.ANON_USER.equals(principalName)) {
			return new AnonymousPrincipal();
		}
		org.sakaiproject.nakamura.api.lite.authorizable.Authorizable ath;
		try {
			ath = authorizableManager.findAuthorizable(principalName);
			if (ath != null) {
				return new SparsePrincipal(ath, this.getClass().getName());
			}
		} catch (AccessDeniedException e) {
			throw new javax.jcr.AccessDeniedException(e.getMessage(), e);
		} catch (StorageClientException e) {
			throw new RepositoryException(e.getMessage(), e);
		}
		return null;
	}

	public void close() {
		try {
			session.logout();
		} catch (ClientPoolException e) {
			LOGGER.warn(e.getMessage(), e);
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * {@link #providePrincipal(String)} is called, if no matching entry is
	 * present in the cache.<br>
	 * NOTE: If the cache is enabled to contain negative entries (see
	 * {@link #NEGATIVE_ENTRY_KEY} configuration option), the cache will also
	 * store negative matches (as <code>null</code> values) in the principal
	 * cache.
	 */
	public Principal getPrincipal(String principalName) {
		checkInitialized();
		if (cache.containsKey(principalName)) {
			return (Principal) cache.get(principalName);
		}
		Principal principal = null;
		try {
			principal = providePrincipal(principalName);
		} catch (RepositoryException e) {
			LOGGER.debug(e.getMessage(), e);
		}
		if (principal != null) {
			cache.put(principalName, principal);
		}
		return principal;
	}

	/**
	 * Check if the instance has been closed {@link #close()}.
	 * 
	 * @throws IllegalStateException
	 *             if this instance was closed.
	 */
	protected void checkInitialized() {
		if (!initialized) {
			throw new IllegalStateException("Not initialized.");
		}
	}

	/**
	 * Clear the principal cache.
	 */
	protected void clearCache() {
		cache.clear();
	}

	/**
	 * Add an entry to the principal cache.
	 * 
	 * @param principal
	 *            to be cached.
	 */
	protected void addToCache(Principal principal) {

	}

	/**
	 * @see PrincipalProvider#init(java.util.Properties)
	 */
	public synchronized void init(Properties options) {
		if (initialized) {
			throw new IllegalStateException("already initialized");
		}

		int maxSize = Integer
				.parseInt(options.getProperty(MAXSIZE_KEY, "1000"));
		cache = new ConcurrentLRUMap<String, Principal>(maxSize);

		initialized = true;
	}

}
