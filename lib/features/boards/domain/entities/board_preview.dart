enum BoardTaskStatus { notStarted, inProgress, blocked, done }

class BoardPreview {
  const BoardPreview({
    required this.id,
    required this.name,
    required this.description,
    required this.columns,
  });

  final String id;
  final String name;
  final String description;
  final List<BoardColumnPreview> columns;

  int get taskCount =>
      columns.fold<int>(0, (total, column) => total + column.tasks.length);
}

class BoardColumnPreview {
  const BoardColumnPreview({
    required this.id,
    required this.name,
    required this.semanticStatus,
    required this.tasks,
    this.wipLimit,
  });

  final String id;
  final String name;
  final BoardTaskStatus semanticStatus;
  final List<BoardTaskPreview> tasks;
  final int? wipLimit;
}

class BoardTaskPreview {
  const BoardTaskPreview({
    required this.id,
    required this.title,
    required this.description,
    required this.status,
    required this.assigneeLabel,
    required this.dueLabel,
    required this.labels,
    required this.priorityLabel,
  });

  final String id;
  final String title;
  final String description;
  final BoardTaskStatus status;
  final String assigneeLabel;
  final String dueLabel;
  final List<String> labels;
  final String priorityLabel;
}
