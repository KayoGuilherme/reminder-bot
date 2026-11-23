package com.kayo.reminder.conventions.rule;

import com.kayo.reminder.conventions.PerFileRule;
import com.kayo.reminder.conventions.SourceFile;
import com.kayo.reminder.conventions.Violation;
import java.util.List;

public class CommentsRule extends PerFileRule {

	@Override
	protected List<Violation> checkFile(SourceFile source) {
		return source.unit().getAllComments().stream().map(comment -> at(source, comment, "comment")).toList();
	}
}
