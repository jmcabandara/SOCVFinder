package jdd.so.bot.actions.cmd;

import java.sql.SQLException;

import org.apache.log4j.Logger;

import fr.tunaki.stackoverflow.chat.Message;
import fr.tunaki.stackoverflow.chat.event.PingMessageEvent;
import jdd.so.api.model.Comment;
import jdd.so.bot.ChatRoom;
import jdd.so.bot.actions.BotCommand;
import jdd.so.nlp.CommentHeatCategory;

public class CommentOffensiveTpCommand extends CommentResponseAbstract {
	/**
	 * Logger for this class
	 */
	private static final Logger logger = Logger.getLogger(CommentOffensiveTpCommand.class);

	@Override
	public String getMatchCommandRegex() {
		return "(?i)(\\stp(\\s|$|,|;|-))";
	}

	@Override
	public int getRequiredAccessLevel() {
		return BotCommand.ACCESS_LEVEL_REVIEWER;
	}

	@Override
	public String getCommandName() {
		return "Confirm rude comment";
	}

	@Override
	public String getCommandDescription() {
		return "Report that comment is offensive";
	}

	@Override
	public String getCommandUsage() {
		return "tp";
	}

	@Override
	public void runCommand(ChatRoom room, PingMessageEvent event) {
		long parentMessage = event.getParentMessageId();
		Message pdm = room.getRoom().getMessage(parentMessage);
		if (pdm == null) {
			room.replyTo(event.getMessage().getId(), "Could not find message your are replying to");
			return;
		}
		String c = pdm.getPlainContent();
		if (!c.contains("#comment")) {
			room.replyTo(event.getMessage().getId(), "Your reply was not direct to an offensive comment");
			return;
		}

		confirm(room, event, c);
	}

	public void confirm(ChatRoom room, PingMessageEvent event, String content) {

		long commentId;
		try {
			commentId = getCommentId(content);
		} catch (RuntimeException e) {
			logger.error("runCommand(ChatRoom, PingMessageEvent)", e);
			room.replyTo(event.getMessage().getId(), "Sorry could not retrive comment id");
			return;
		}

		try {
			saveToDatabase(commentId, true);
		} catch (SQLException e) {
			logger.error("confirm(ChatRoom, PingMessageEvent, String)", e);
		}

		if (event.getMessage().getPlainContent().toUpperCase().contains("SOCVR")) {
			// Load it from API again
			Comment c = getCommentFromApi(commentId);
			CommentHeatCategory cc = room.getBot().getCommentCategory();
			if (c != null && cc != null) {
				try {
					cc.classifyComment(c);
					StringBuilder message = room.getBot().getCommentsController().getHeatMessageResult(c, c.getLink());
					message.append(" Confirmed by: ").append(event.getUserName());
					room.getBot().getSOCVRRoom().send(message.toString());
				} catch (Exception e) {
					logger.error("confirm(ChatRoom, PingMessageEvent, String)", e);
				}
			}else{
				room.replyTo(event.getMessage().getId(), "Sorry, could not retrive comment from api, maybe already deleted?");
			}
		}

		String edit = getEdit(event, content, true);

		room.edit(event.getParentMessageId(), content + edit).handleAsync((mId, thr) -> {
			return mId;
		});

	}

}
