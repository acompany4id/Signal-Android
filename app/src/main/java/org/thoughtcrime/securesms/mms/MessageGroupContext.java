package org.thoughtcrime.securesms.mms;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.storageservice.protos.groups.local.DecryptedMember;
import org.signal.zkgroup.InvalidInputException;
import org.signal.zkgroup.groups.GroupMasterKey;
import org.thoughtcrime.securesms.database.model.databaseprotos.DecryptedGroupV2Context;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.Base64;
import org.whispersystems.signalservice.api.groupsv2.DecryptedGroupUtil;
import org.whispersystems.signalservice.api.push.SignalServiceAddress;
import org.whispersystems.signalservice.api.util.UuidUtil;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContext;
import org.whispersystems.signalservice.internal.push.SignalServiceProtos.GroupContextV2;

import java.io.IOException;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

/**
 * Represents either a GroupV1 or GroupV2 encoded context.
 */
public final class MessageGroupContext {

  private final String            encodedGroupContext;
  private final GroupV1Properties groupV1;
  private final GroupV2Properties groupV2;

  public MessageGroupContext(@NonNull String encodedGroupContext, boolean v2)
      throws IOException
  {
    this.encodedGroupContext = encodedGroupContext;
    if (v2) {
      this.groupV1 = null;
      this.groupV2 = new GroupV2Properties(DecryptedGroupV2Context.parseFrom(Base64.decode(encodedGroupContext)));
    } else {
      this.groupV1 = new GroupV1Properties(GroupContext.parseFrom(Base64.decode(encodedGroupContext)));
      this.groupV2 = null;
    }
  }

  public MessageGroupContext(@NonNull GroupContext group) {
    this.encodedGroupContext = Base64.encodeBytes(group.toByteArray());
    this.groupV1             = new GroupV1Properties(group);
    this.groupV2             = null;
  }

  public MessageGroupContext(@NonNull DecryptedGroupV2Context group) {
    this.encodedGroupContext = Base64.encodeBytes(group.toByteArray());
    this.groupV1             = null;
    this.groupV2             = new GroupV2Properties(group);
  }

  public @NonNull GroupV1Properties requireGroupV1Properties() {
    if (groupV1 == null) {
      throw new AssertionError();
    }
    return groupV1;
  }

  public @NonNull GroupV2Properties requireGroupV2Properties() {
    if (groupV2 == null) {
      throw new AssertionError();
    }
    return groupV2;
  }

  public boolean isV2Group() {
    return groupV2 != null;
  }

  public @NonNull String getEncodedGroupContext() {
    return encodedGroupContext;
  }

  public String getName() {
    return isV2Group() ? groupV2.decryptedGroupV2Context.getGroupState().getTitle()
                       : groupV1.getGroupContext().getName();
  }

  public List<RecipientId> getMembersList() {
    if (isV2Group()) {
      List<RecipientId> members = new LinkedList<>();
      for (DecryptedMember member : groupV2.decryptedGroupV2Context.getGroupState().getMembersList()) {
        RecipientId recipient = RecipientId.from(UuidUtil.fromByteString(member.getUuid()), null);
        if (!Recipient.self().getId().equals(recipient)) {
          members.add(recipient);
        }
      }
      return members;
    } else {
      List<GroupContext.Member> membersList = groupV1.groupContext.getMembersList();
      if (membersList.isEmpty()) {
        return Collections.emptyList();
      } else {
        LinkedList<RecipientId> members = new LinkedList<>();

        for (GroupContext.Member member : membersList) {
          RecipientId recipient = RecipientId.from(UuidUtil.parseOrNull(member.getUuid()), member.getE164());
          if (!Recipient.self().getId().equals(recipient)) {
            members.add(recipient);
          }
        }
        return members;
      }
    }
  }

  public static class GroupV1Properties {

    private final GroupContext groupContext;

    private GroupV1Properties(GroupContext groupContext) {
      this.groupContext = groupContext;
    }

    public @NonNull GroupContext getGroupContext() {
      return groupContext;
    }

    public boolean isQuit() {
      return groupContext.getType().getNumber() == GroupContext.Type.QUIT_VALUE;
    }

    public boolean isUpdate() {
      return groupContext.getType().getNumber() == GroupContext.Type.UPDATE_VALUE;
    }
  }

  public static class GroupV2Properties {

    private final DecryptedGroupV2Context decryptedGroupV2Context;
    private final GroupContextV2          groupContext;
    private final GroupMasterKey          groupMasterKey;

    private GroupV2Properties(DecryptedGroupV2Context decryptedGroupV2Context) {
      this.decryptedGroupV2Context = decryptedGroupV2Context;
      this.groupContext            = decryptedGroupV2Context.getContext();
      try {
        groupMasterKey = new GroupMasterKey(groupContext.getMasterKey().toByteArray());
      } catch (InvalidInputException e) {
        throw new AssertionError(e);
      }
    }

    public @NonNull GroupContextV2 getGroupContext() {
      return groupContext;
    }

    public @NonNull GroupMasterKey getGroupMasterKey() {
      return groupMasterKey;
    }

    public @NonNull List<UUID> getActiveMembers() {
      return DecryptedGroupUtil.membersToUuidList(decryptedGroupV2Context.getGroupState().getMembersList());
    }

    public @NonNull List<UUID> getPendingMembers() {
      return DecryptedGroupUtil.pendingToUuidList(decryptedGroupV2Context.getGroupState().getPendingMembersList());
    }

    public @NonNull List<UUID> getRemovedMembers() {
      return DecryptedGroupUtil.removedMembersUuidList(decryptedGroupV2Context.getChange());
    }

    public boolean isUpdate() {
      // The group context is only stored on update messages.
      return true;
    }
  }
}
