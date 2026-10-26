import 'package:flutter/material.dart';

/// Central motion tokens for Weave.
///
/// Product widgets consume these semantic values instead of defining local
/// durations and curves. Reduced motion preserves content, focus, semantics,
/// and status feedback while making non-essential transitions immediate.
@immutable
class WeaveMotionTheme extends ThemeExtension<WeaveMotionTheme> {
  const WeaveMotionTheme({
    this.profileReducedMotion = false,
    this.quick = const Duration(milliseconds: 120),
    this.standard = const Duration(milliseconds: 200),
    this.emphasized = const Duration(milliseconds: 320),
    this.standardCurve = Curves.easeOutCubic,
    this.emphasizedCurve = Curves.easeInOutCubic,
  });

  final bool profileReducedMotion;
  final Duration quick;
  final Duration standard;
  final Duration emphasized;
  final Curve standardCurve;
  final Curve emphasizedCurve;

  WeaveMotionSpec resolve(BuildContext context) {
    final platform =
        WidgetsBinding.instance.platformDispatcher.accessibilityFeatures;
    final mediaQueryReduced =
        MediaQuery.maybeOf(context)?.disableAnimations ?? false;
    final reduced =
        profileReducedMotion || mediaQueryReduced || platform.reduceMotion;

    return reduced
        ? const WeaveMotionSpec(
            quick: Duration.zero,
            standard: Duration.zero,
            emphasized: Duration.zero,
            standardCurve: Curves.linear,
            emphasizedCurve: Curves.linear,
            reduced: true,
          )
        : WeaveMotionSpec(
            quick: quick,
            standard: standard,
            emphasized: emphasized,
            standardCurve: standardCurve,
            emphasizedCurve: emphasizedCurve,
            reduced: false,
          );
  }

  @override
  WeaveMotionTheme copyWith({
    bool? profileReducedMotion,
    Duration? quick,
    Duration? standard,
    Duration? emphasized,
    Curve? standardCurve,
    Curve? emphasizedCurve,
  }) {
    return WeaveMotionTheme(
      profileReducedMotion: profileReducedMotion ?? this.profileReducedMotion,
      quick: quick ?? this.quick,
      standard: standard ?? this.standard,
      emphasized: emphasized ?? this.emphasized,
      standardCurve: standardCurve ?? this.standardCurve,
      emphasizedCurve: emphasizedCurve ?? this.emphasizedCurve,
    );
  }

  @override
  WeaveMotionTheme lerp(
    covariant ThemeExtension<WeaveMotionTheme>? other,
    double t,
  ) {
    if (other is! WeaveMotionTheme) return this;
    return WeaveMotionTheme(
      profileReducedMotion: t < 0.5
          ? profileReducedMotion
          : other.profileReducedMotion,
      quick: _lerpDuration(quick, other.quick, t),
      standard: _lerpDuration(standard, other.standard, t),
      emphasized: _lerpDuration(emphasized, other.emphasized, t),
      standardCurve: t < 0.5 ? standardCurve : other.standardCurve,
      emphasizedCurve: t < 0.5 ? emphasizedCurve : other.emphasizedCurve,
    );
  }

  static Duration _lerpDuration(Duration from, Duration to, double t) {
    return Duration(
      microseconds:
          (from.inMicroseconds + (to.inMicroseconds - from.inMicroseconds) * t)
              .round(),
    );
  }
}

@immutable
class WeaveMotionSpec {
  const WeaveMotionSpec({
    required this.quick,
    required this.standard,
    required this.emphasized,
    required this.standardCurve,
    required this.emphasizedCurve,
    required this.reduced,
  });

  final Duration quick;
  final Duration standard;
  final Duration emphasized;
  final Curve standardCurve;
  final Curve emphasizedCurve;
  final bool reduced;
}

extension WeaveMotionContext on BuildContext {
  WeaveMotionSpec get weaveMotion =>
      (Theme.of(this).extension<WeaveMotionTheme>() ?? const WeaveMotionTheme())
          .resolve(this);
}
