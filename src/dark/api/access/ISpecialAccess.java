package dark.api.access;

import java.util.List;

/** Used by any object that needs to restrict access to it by a set of usernames
 *
 * @author DarkGuardsman */
public interface ISpecialAccess
{
    /** Gets the player's access level on the machine he is using
     *
     * @return access level of the player, make sure to return no access if the player doesn't have
     * any */
    public AccessUser getUserAccess(String username);

    /** gets the access list for the machine
     *
     * @return hasMap of players and there access levels */
    public List<AccessUser> getUsers();

    /** sets the players access level in the access map
     *
     * @param player
     * @return true if the level was set false if something went wrong */
    public boolean setUserAccess(String username, AccessGroup group, boolean save);

    /** Removes the user from the access list
     *
     * @param username
     * @return */
    public boolean removeUserAccess(String username);

    public AccessGroup getGroup(String name);

    public void addGroup(AccessGroup group);

}
