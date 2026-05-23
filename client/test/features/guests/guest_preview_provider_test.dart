import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:weave/features/guests/domain/entities/guest_preview.dart';
import 'package:weave/features/guests/presentation/providers/guest_preview_provider.dart';

void main() {
  test('guest preview provider covers all safe guest states', () {
    final container = ProviderContainer.test();
    addTearDown(container.dispose);

    final guests = container.read(guestPreviewProvider);
    expect(
      guests.map((guest) => guest.status).toSet(),
      containsAll(<GuestPreviewStatus>{
        GuestPreviewStatus.pending,
        GuestPreviewStatus.active,
        GuestPreviewStatus.disabled,
        GuestPreviewStatus.expired,
      }),
    );
    expect(guests.any((guest) => guest.canSeeMemberOnlyAffordances), isFalse);
  });
}
